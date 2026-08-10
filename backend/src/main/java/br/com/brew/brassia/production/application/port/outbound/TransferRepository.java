package br.com.brew.brassia.production.application.port.outbound;

import br.com.brew.brassia.production.domain.BatchTransfer;
import java.util.Optional;
import java.util.UUID;

public interface TransferRepository {
    void insert(BatchTransfer transfer);

    Optional<BatchTransfer> findByBatch(UUID breweryId, UUID batchId);

    /**
     * O lote que ocupa o equipamento agora: transferido para ele e ainda fermentando.
     *
     * <p>A junção com o estado do lote é o que impede a resposta de envelhecer. Sem ela, o último lote
     * transferido continuaria "ocupando" o tanque para sempre — inclusive depois de envasado — e a
     * telemetria de um lote novo seria creditada ao anterior.
     */
    Optional<UUID> findFermentingBatchByEquipment(UUID breweryId, UUID equipmentId);
}
