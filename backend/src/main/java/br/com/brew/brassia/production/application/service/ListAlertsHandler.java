package br.com.brew.brassia.production.application.service;

import br.com.brew.brassia.production.application.port.inbound.ListAlertsUseCase;
import br.com.brew.brassia.production.application.port.outbound.AlertRepository;
import br.com.brew.brassia.production.application.port.outbound.BatchRepository;
import br.com.brew.brassia.production.domain.BatchAlert;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ListAlertsHandler implements ListAlertsUseCase {

    private final BatchRepository batches;
    private final AlertRepository alerts;

    public ListAlertsHandler(BatchRepository batches, AlertRepository alerts) {
        this.batches = Objects.requireNonNull(batches);
        this.alerts = Objects.requireNonNull(alerts);
    }

    @Override
    public List<BatchAlert> handle(UUID breweryId, UUID batchId) {
        batches.findById(breweryId, batchId)
                .orElseThrow(() -> new IllegalArgumentException("lote inexistente"));
        return alerts.findByBatch(breweryId, batchId);
    }
}
