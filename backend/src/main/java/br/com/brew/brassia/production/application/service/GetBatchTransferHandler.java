package br.com.brew.brassia.production.application.service;

import br.com.brew.brassia.production.application.port.inbound.GetBatchTransferUseCase;
import br.com.brew.brassia.production.application.port.outbound.BatchRepository;
import br.com.brew.brassia.production.application.port.outbound.TransferRepository;
import br.com.brew.brassia.production.domain.BatchTransfer;
import br.com.brew.brassia.production.domain.UnknownBatchException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class GetBatchTransferHandler implements GetBatchTransferUseCase {

    private final BatchRepository batches;
    private final TransferRepository transfers;

    public GetBatchTransferHandler(BatchRepository batches, TransferRepository transfers) {
        this.batches = Objects.requireNonNull(batches);
        this.transfers = Objects.requireNonNull(transfers);
    }

    @Override
    public Optional<BatchTransfer> handle(UUID breweryId, UUID batchId) {
        batches.findById(breweryId, batchId)
                .orElseThrow(() -> new UnknownBatchException(batchId));
        return transfers.findByBatch(breweryId, batchId);
    }
}
