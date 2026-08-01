package br.com.brew.brassia.production;

import java.time.Instant;
import java.util.UUID;

/**
 * Abertura de alerta na central do lote (PRD-006), publicada para outros módulos — a agenda
 * de fermentação (FER-004) sinaliza etapa atrasada aqui em vez de manter uma segunda central.
 *
 * <p>Alerta é aviso: nunca altera setpoint, equipamento ou o estado do lote.
 */
public interface BatchAlertPublisher {
    UUID openStepAlert(UUID breweryId, UUID actorId, UUID batchId, String message, Instant plannedAt,
            Instant occurredAt);
}
