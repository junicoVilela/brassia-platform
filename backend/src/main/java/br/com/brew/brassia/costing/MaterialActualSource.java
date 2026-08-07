package br.com.brew.brassia.costing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * O que o estoque sabe do material de uma ordem (CST-002): o que ela separou e o que ela consumiu.
 *
 * <p><strong>A porta é do custo e o estoque a implementa</strong>, como o {@code CostContributor} —
 * e aqui não há escolha: o estoque já depende do custo para contribuir parcelas, então o custo não
 * pode depender do estoque de volta. O {@code ModularityTest} pegaria o ciclo.
 *
 * <p><strong>Reservado é a base de preço; consumido é o fato.</strong> A plataforma não tem custo
 * padrão — ninguém cadastra "quanto o malte deveria custar". O que existe é a decisão que a ordem
 * tomou quando separou lotes concretos a preços concretos: era com aquele preço que se contava.
 * Comparar o consumo contra ele responde à pergunta real do brewer — "paguei mais caro do que o
 * que eu tinha separado?" — sem inventar um padrão que a casa nunca definiu.
 *
 * <p>Quantidades vêm na <strong>unidade canônica</strong> (KG, L, UNIT), porque o plano vem da
 * receita e a receita fala em grama onde o lote fala em quilo.
 */
public interface MaterialActualSource {

    Actuals actualsFor(UUID breweryId, UUID orderId);

    /**
     * @param reserved o que a ordem separou, com o preço dos lotes separados — a base de preço
     * @param consumed o que a brassagem confirmou ter usado, com o preço dos lotes que saíram
     */
    record Actuals(List<MaterialFact> reserved, List<MaterialFact> consumed) {

        public Actuals {
            reserved = List.copyOf(reserved);
            consumed = List.copyOf(consumed);
        }

        public static Actuals empty() {
            return new Actuals(List.of(), List.of());
        }
    }

    /**
     * Um ingrediente, agregado.
     *
     * <p>O custo total viaja em vez do preço unitário de propósito: quando a ordem separou três
     * lotes do mesmo malte a preços diferentes, o preço da base é a média ponderada, e ponderar
     * aqui evitaria que cada consumidor da porta a calculasse do seu jeito.
     */
    record MaterialFact(UUID ingredientId, String name, BigDecimal quantity, String unit,
            BigDecimal totalCost) {

        public MaterialFact {
            Objects.requireNonNull(ingredientId, "ingrediente é obrigatório");
            Objects.requireNonNull(quantity, "quantidade é obrigatória");
            Objects.requireNonNull(unit, "unidade é obrigatória");
            totalCost = totalCost == null ? BigDecimal.ZERO : totalCost;
        }

        /** Preço médio ponderado; vazio quando não há quantidade da qual tirar média. */
        public BigDecimal unitCost() {
            if (quantity.signum() <= 0) {
                return null;
            }
            return totalCost.divide(quantity, 6, RoundingMode.HALF_UP);
        }
    }
}
