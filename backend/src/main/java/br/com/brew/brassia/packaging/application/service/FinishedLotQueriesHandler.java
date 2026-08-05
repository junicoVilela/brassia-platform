package br.com.brew.brassia.packaging.application.service;

import br.com.brew.brassia.packaging.application.port.inbound.FinishedLotQueries;
import br.com.brew.brassia.packaging.application.port.outbound.FinishedLotRepository;
import br.com.brew.brassia.packaging.domain.FinishedLot;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class FinishedLotQueriesHandler implements FinishedLotQueries {

    private final FinishedLotRepository lots;

    public FinishedLotQueriesHandler(FinishedLotRepository lots) {
        this.lots = Objects.requireNonNull(lots);
    }

    @Override
    public List<FinishedLot> all(UUID breweryId) {
        return lots.findAll(breweryId);
    }

    @Override
    public List<FinishedLot> byBatch(UUID breweryId, UUID batchId) {
        return lots.findByBatch(breweryId, batchId);
    }
}
