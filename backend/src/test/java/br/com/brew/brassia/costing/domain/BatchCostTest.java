package br.com.brew.brassia.costing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.brew.brassia.costing.CostContributor.CostCategory;
import br.com.brew.brassia.costing.CostContributor.CostGap;
import br.com.brew.brassia.costing.CostContributor.CostLine;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** O que o custo soma, como ele divide e o que ele se recusa a esconder (CST-001). */
class BatchCostTest {

    private static final Instant NOW = Instant.parse("2026-08-06T10:00:00Z");

    @Test
    @DisplayName("o total é a soma das parcelas, e o por litro divide pelo volume que existiu")
    void somaEDivide() {
        var cost = open(BigDecimal.valueOf(390), line(CostCategory.INGREDIENT, "Malte", 100),
                line(CostCategory.PACKAGING, "Lata", 95));

        assertThat(cost.total()).isEqualByComparingTo("195");
        assertThat(cost.costPerLiter()).isEqualByComparingTo("0.5000");
    }

    @Test
    @DisplayName("o total por categoria é como o custo é lido: quanto foi malte, quanto foi lata")
    void separaPorCategoria() {
        var cost = open(BigDecimal.valueOf(100), line(CostCategory.INGREDIENT, "Malte", 60),
                line(CostCategory.INGREDIENT, "Lúpulo", 20),
                line(CostCategory.PACKAGING, "Lata", 20));

        assertThat(cost.totalByCategory().get(CostCategory.INGREDIENT)).isEqualByComparingTo("80");
        assertThat(cost.totalByCategory().get(CostCategory.PACKAGING)).isEqualByComparingTo("20");
    }

    @Test
    @DisplayName("custo com lacuna se declara incompleto: o total é menor que a verdade")
    void lacunaDeixaOCustoIncompleto() {
        var cost = BatchCost.open(UUID.randomUUID(), UUID.randomUUID(), "LOTE-100",
                BigDecimal.valueOf(390), List.of(line(CostCategory.INGREDIENT, "Malte", 100)),
                List.of(new CostGap(CostCategory.LABOR, "não há hora trabalhada registrada")));

        assertThat(cost.incomplete()).isTrue();
        // O total continua sendo o que foi somado: a lacuna não vira estimativa inventada.
        assertThat(cost.total()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("fechar congela e só acontece uma vez")
    void fechaUmaVez() {
        var cost = open(BigDecimal.valueOf(390), line(CostCategory.INGREDIENT, "Malte", 100));

        var closed = cost.close(UUID.randomUUID(), "apuração do mês", NOW);

        assertThat(closed.closed()).isTrue();
        assertThat(closed.closedAt()).isEqualTo(NOW);
        assertThat(cost.closed()).as("fechar devolve um novo custo; o aberto não muda").isFalse();
        assertThatThrownBy(() -> closed.close(UUID.randomUUID(), "de novo", NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("lote sem volume não fecha: não há por que dividir o custo")
    void semVolumeNaoFecha() {
        var cost = open(BigDecimal.ZERO, line(CostCategory.INGREDIENT, "Malte", 100));

        assertThatThrownBy(() -> cost.close(UUID.randomUUID(), "tentativa", NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    private static BatchCost open(BigDecimal volume, CostLine... lines) {
        return BatchCost.open(UUID.randomUUID(), UUID.randomUUID(), "LOTE-100", volume, List.of(lines),
                List.of());
    }

    private static CostLine line(CostCategory category, String description, int total) {
        return new CostLine(category, description, "origem de teste", BigDecimal.ONE, "KG",
                BigDecimal.valueOf(total), BigDecimal.valueOf(total));
    }
}
