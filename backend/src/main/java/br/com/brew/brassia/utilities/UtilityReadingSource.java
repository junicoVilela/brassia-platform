package br.com.brew.brassia.utilities;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Fonte de medição de utilidade (UTL-001), implementada por cada módulo que mede.
 *
 * <p>Mesma inversão do {@code LineageSource} e do {@code CostContributor}: o indicador não lê
 * sanitização nem gás, ele pergunta. Hoje respondem dois — o ciclo de limpeza, com água e energia,
 * e a conexão de gás, com CO₂. Um medidor novo entra implementando a porta.
 *
 * <p><strong>A cobertura vem junto com a soma, e é metade da resposta.</strong> Um indicador de
 * 3 L/L calculado sobre um terço dos ciclos não é um bom indicador — é um indicador de um terço da
 * fábrica. Quem responde pela medição é quem sabe quantos eventos deveriam ter sido medidos, e por
 * isso a cobertura é declarada aqui, e não estimada pelo indicador.
 */
public interface UtilityReadingSource {

    /** Medições do período, uma por evento medido. */
    List<Reading> readingsIn(UUID breweryId, Instant from, Instant to);

    /** O quanto do período foi de fato medido. Sem cobertura declarada, o indicador não afirma nada. */
    default List<Coverage> coverageIn(UUID breweryId, Instant from, Instant to) {
        return List.of();
    }

    /** O que se mede. Ampliar esta lista é ampliar o que a cervejaria consegue enxergar. */
    enum UtilityType {
        /** Água, em litros. */
        WATER,
        /** Energia, em kWh. */
        ENERGY,
        /** CO₂, em kg. */
        CO2,
        /** Produto de limpeza, em kg. */
        CLEANING_PRODUCT
    }

    /**
     * Uma medição.
     *
     * @param measured falso quando o número foi estimado por alguma regra, e não lido de um
     *                 medidor. Hoje nada estima — e é justamente por isso que o campo existe: no
     *                 dia em que alguém estimar, o indicador vai poder dizer quanto do total é
     *                 leitura e quanto é conta de padeiro, em vez de misturar os dois
     * @param source   de onde veio, em texto legível ("ciclo CIP-4488 na linha de envase")
     */
    record Reading(UtilityType type, BigDecimal amount, Instant at, String source, boolean measured) {

        public Reading {
            Objects.requireNonNull(type, "tipo é obrigatório");
            Objects.requireNonNull(amount, "quantidade é obrigatória");
            Objects.requireNonNull(at, "instante é obrigatório");
            Objects.requireNonNull(source, "origem é obrigatória");
        }
    }

    /**
     * @param reported quantos eventos do período tiveram medição registrada
     * @param expected quantos deveriam ter tido
     * @param what     o que são esses eventos, para a resposta poder dizer "12 de 15 ciclos"
     */
    record Coverage(String what, int reported, int expected) {

        public Coverage {
            Objects.requireNonNull(what, "descrição da cobertura é obrigatória");
        }

        public boolean complete() {
            return reported >= expected;
        }
    }
}
