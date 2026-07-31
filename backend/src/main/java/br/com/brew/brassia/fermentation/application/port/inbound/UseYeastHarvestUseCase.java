package br.com.brew.brassia.fermentation.application.port.inbound;

import java.util.UUID;

/**
 * Confirma o uso de uma coleta num lote (YST-002). Consome a coleta; exige confirmação
 * explícita e lote de destino.
 */
public interface UseYeastHarvestUseCase {
    void handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID harvestId, UUID targetBatchId, boolean confirmed) {}
}
