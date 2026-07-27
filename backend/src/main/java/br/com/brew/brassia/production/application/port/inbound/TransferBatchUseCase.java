package br.com.brew.brassia.production.application.port.inbound;

import br.com.brew.brassia.production.domain.BatchTransfer;
import java.math.BigDecimal;
import java.util.UUID;

/** Transfere o lote ao fermentador (PRD-005): valida capacidade e balanço de massa. */
public interface TransferBatchUseCase {
    BatchTransfer handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID batchId, UUID destinationEquipmentId, BigDecimal volumeLiters,
            BigDecimal ogSg, BigDecimal lossesLiters) {}
}
