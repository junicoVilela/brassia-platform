package br.com.brew.brassia.production;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * O que o lote rendeu de fato (CST-002): volume planejado, volume transferido e perda declarada
 * na transferência.
 *
 * <p>Separado do {@link BatchLookup} porque responde outra pergunta. Aquele identifica o lote para
 * quem quer pendurar dados nele; este compara intenção com resultado, e só faz sentido depois que o
 * dia de brassa terminou.
 */
public interface BatchOutcomeLookup {

    Optional<BatchOutcome> outcomeOf(UUID breweryId, UUID batchId);

    /**
     * @param transferredVolumeLiters volume que chegou ao fermentador; vazio enquanto não houve
     *                                transferência — e vazio não é zero: o lote pode estar fervendo
     * @param transferLossesLiters    perda declarada na transferência; vazia pelo mesmo motivo
     */
    record BatchOutcome(BigDecimal plannedVolumeLiters, BigDecimal transferredVolumeLiters,
            BigDecimal transferLossesLiters) {

        public boolean transferred() {
            return transferredVolumeLiters != null;
        }
    }
}
