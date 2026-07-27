package br.com.brew.brassia.production.application.service;

import br.com.brew.brassia.production.application.port.inbound.ListAppliedCorrectionsUseCase;
import br.com.brew.brassia.production.application.port.outbound.AppliedCorrectionRepository;
import br.com.brew.brassia.production.application.port.outbound.BatchRepository;
import br.com.brew.brassia.production.domain.AppliedCorrection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ListAppliedCorrectionsHandler implements ListAppliedCorrectionsUseCase {

    private final BatchRepository batches;
    private final AppliedCorrectionRepository corrections;

    public ListAppliedCorrectionsHandler(BatchRepository batches, AppliedCorrectionRepository corrections) {
        this.batches = Objects.requireNonNull(batches);
        this.corrections = Objects.requireNonNull(corrections);
    }

    @Override
    public List<AppliedCorrection> handle(UUID breweryId, UUID batchId) {
        batches.findById(breweryId, batchId)
                .orElseThrow(() -> new IllegalArgumentException("lote inexistente"));
        return corrections.findByBatch(breweryId, batchId);
    }
}
