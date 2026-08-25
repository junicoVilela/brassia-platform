package br.com.brew.brassia.production.domain;

import java.util.UUID;

/**
 * O alerta pedido no endereço não existe neste lote (DEB-PRD-002).
 *
 * <p><strong>Cobre os dois casos de propósito:</strong> o alerta que não existe em cervejaria nenhuma e o
 * que existe, mas pertence a outro lote. O endereço é o par {@code /batches/{id}/alerts/{alertId}}, e
 * responder diferente para cada um contaria a quem tem o identificador que o alerta existe noutro lugar —
 * a mesma fresta que {@link UnknownBatchException} fecha entre cervejarias.
 */
public final class UnknownAlertException extends RuntimeException {

    private final UUID alertId;

    public UnknownAlertException(UUID alertId) {
        super("alerta inexistente: " + alertId);
        this.alertId = alertId;
    }

    public UUID alertId() {
        return alertId;
    }
}
