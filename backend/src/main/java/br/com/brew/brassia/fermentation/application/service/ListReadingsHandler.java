package br.com.brew.brassia.fermentation.application.service;

import br.com.brew.brassia.fermentation.application.port.inbound.ListReadingsUseCase;
import br.com.brew.brassia.fermentation.application.port.outbound.ReadingRepository;
import br.com.brew.brassia.fermentation.domain.FermentationReading;
import br.com.brew.brassia.fermentation.domain.ReadingKind;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ListReadingsHandler implements ListReadingsUseCase {

    private final ReadingRepository readings;

    public ListReadingsHandler(ReadingRepository readings) {
        this.readings = Objects.requireNonNull(readings);
    }

    @Override
    public List<FermentationReading> handle(UUID breweryId, UUID batchId, String kind) {
        var filter = kind == null || kind.isBlank() ? null : ReadingKind.of(kind);
        return readings.findSeries(breweryId, batchId, filter);
    }
}
