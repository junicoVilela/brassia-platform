package br.com.brew.brassia.water.application.service;

import br.com.brew.brassia.water.application.port.inbound.ListWaterReportsUseCase;
import br.com.brew.brassia.water.application.port.outbound.WaterReportRepository;
import br.com.brew.brassia.water.domain.WaterReport;
import java.util.List;
import java.util.Objects;

public final class ListWaterReportsHandler implements ListWaterReportsUseCase {
    private final WaterReportRepository repository;

    public ListWaterReportsHandler(WaterReportRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Override
    public List<Report> handle(Query query) {
        return repository.findBySource(query.breweryId(), query.sourceId())
                .stream().map(ListWaterReportsHandler::toReport).toList();
    }

    private static Report toReport(WaterReport r) {
        var ions = r.ions();
        return new Report(r.id().value(), r.sourceId().value(), r.collectedOn(), r.method().name(),
                ions.calcium(), ions.magnesium(), ions.sodium(), ions.sulfate(), ions.chloride(),
                ions.bicarbonate(), r.notes());
    }
}
