package br.com.brew.brassia.production.domain;

import java.util.UUID;

/**
 * A etapa pedida no endereço não existe neste lote (DEB-PRD-002).
 *
 * <p>Vem junto com {@link UnknownBatchException} porque deixá-la em {@code 400} enquanto o lote passa a
 * responder {@code 404} criaria, no mesmo controlador, a inconsistência que o débito veio remover.
 *
 * <p><strong>Etapa de outro lote responde igual a etapa que não existe</strong>, pela mesma razão do
 * lote: o par no endereço é {@code /batches/{id}/steps/{stepId}}, e distinguir os dois casos diria a quem
 * tem o identificador que a etapa existe em algum lugar.
 *
 * <p>Não confundir com a etapa informada <em>no corpo</em> de uma medição: ali o problema é um campo, a
 * resposta continua {@code 400}, e o que quem opera precisa saber é qual campo consertar.
 */
public final class UnknownStepException extends RuntimeException {

    private final UUID batchId;
    private final UUID stepId;

    public UnknownStepException(UUID batchId, UUID stepId) {
        super("etapa inexistente no lote " + batchId + ": " + stepId);
        this.batchId = batchId;
        this.stepId = stepId;
    }

    public UUID batchId() {
        return batchId;
    }

    public UUID stepId() {
        return stepId;
    }
}
