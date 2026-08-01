package br.com.brew.brassia.fermentation.application.port.inbound;

import java.util.List;
import java.util.UUID;

/**
 * Abre alerta na central do lote para cada etapa vencida além da tolerância (FER-004).
 * Alerta é aviso: não altera setpoint, equipamento nem o estado da etapa.
 */
public interface RaiseLateStepAlertsUseCase {
    List<UUID> handle(UUID actorId, UUID breweryId);
}
