package br.com.brew.brassia.costing;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Fonte de parcelas de custo (CST-001), implementada por cada módulo dono do gasto.
 *
 * <p><strong>Mesma inversão da genealogia, e pelo mesmo motivo.</strong> Somar o custo de um lote
 * exigiria ler estoque, envase, sanitização e gás — o que as fronteiras de módulo existem para
 * impedir. Em vez disso, cada módulo responde pelo que ele sabe custar, e o custo só junta.
 *
 * <p>O efeito colateral é o melhor argumento: módulo que não implementa esta porta não contribui
 * parcela nenhuma, e a ausência aparece <strong>como lacuna declarada</strong> — em vez de virar um
 * zero somado no meio do total. Um custo sem mão de obra que diz "sem mão de obra" é utilizável; um
 * que a soma como zero mente por omissão.
 */
public interface CostContributor {

    /** As parcelas que este módulo conhece para o escopo. */
    List<CostLine> linesFor(UUID breweryId, CostScope scope);

    /** O que este módulo <em>deveria</em> saber custar e ainda não sabe, com o motivo. */
    default List<CostGap> gapsFor(UUID breweryId, CostScope scope) {
        return List.of();
    }

    /**
     * O recorte do custo: o lote e a ordem que o gerou.
     *
     * <p>Só isso, e é de propósito. Quem sabe o que mais pertence ao lote — os planos de envase, os
     * ciclos de limpeza do tanque — é o módulo dono daquele dado, e é ele quem resolve o recorte
     * pela consulta publicada de quem o tem. Enfiar tudo aqui obrigaria o custo a conhecer o mundo
     * inteiro para poder somá-lo.
     */
    record CostScope(UUID batchId, UUID orderId) {

        public CostScope {
            Objects.requireNonNull(batchId, "lote é obrigatório");
            Objects.requireNonNull(orderId, "ordem é obrigatória");
        }
    }

    /**
     * Uma parcela do custo.
     *
     * @param source de onde o número veio, em texto legível — é o critério da história: "origem de
     *               cada parcela é rastreável". Um total sem origem é um número que ninguém explica
     *               seis meses depois
     */
    record CostLine(CostCategory category, String description, String source, BigDecimal quantity,
            String unit, BigDecimal unitCost, BigDecimal total) {

        public CostLine {
            Objects.requireNonNull(category, "categoria é obrigatória");
            Objects.requireNonNull(description, "descrição é obrigatória");
            Objects.requireNonNull(source, "origem da parcela é obrigatória");
            Objects.requireNonNull(total, "total é obrigatório");
        }
    }

    /** Parcela que falta, e por quê. */
    record CostGap(CostCategory category, String reason) {

        public CostGap {
            Objects.requireNonNull(category, "categoria é obrigatória");
            Objects.requireNonNull(reason, "motivo da lacuna é obrigatório");
        }
    }

    /** As parcelas que um custo de lote pode ter. Ampliar esta lista é ampliar o que se mede. */
    enum CostCategory {
        INGREDIENT,
        PACKAGING,
        UTILITY,
        LABOR
    }
}
