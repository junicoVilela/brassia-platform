package br.com.brew.brassia.water.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.water.application.port.inbound.RecordWaterReportUseCase;
import br.com.brew.brassia.water.application.port.outbound.WaterReportRepository;
import br.com.brew.brassia.water.application.port.outbound.WaterSourceRepository;
import br.com.brew.brassia.water.domain.IonProfile;
import br.com.brew.brassia.water.domain.WaterMethod;
import br.com.brew.brassia.water.domain.WaterReport;
import br.com.brew.brassia.water.domain.WaterSourceId;
import java.util.Map;
import java.util.Objects;

public final class RecordWaterReportHandler implements RecordWaterReportUseCase {
    private final WaterSourceRepository sources;
    private final WaterReportRepository reports;
    private final AuditTrail audit;

    public RecordWaterReportHandler(WaterSourceRepository sources, WaterReportRepository reports, AuditTrail audit) {
        this.sources = Objects.requireNonNull(sources);
        this.reports = Objects.requireNonNull(reports);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        if (sources.findById(command.breweryId(), command.sourceId()).isEmpty()) {
            throw new IllegalArgumentException("fonte inexistente");
        }
        var method = WaterMethod.of(command.method());
        var ions = new IonProfile(command.calcium(), command.magnesium(), command.sodium(),
                command.sulfate(), command.chloride(), command.bicarbonate());

        var report = WaterReport.record(command.breweryId(), new WaterSourceId(command.sourceId()),
                command.collectedOn(), method, ions, command.notes());
        reports.insert(report);
        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "water.report.record",
                "water_report", report.id().value().toString(),
                Map.of("sourceId", command.sourceId().toString(), "method", method.name())));

        return new Result(report.id().value(), command.sourceId(), report.collectedOn(), method.name(),
                ions.calcium(), ions.magnesium(), ions.sodium(), ions.sulfate(), ions.chloride(),
                ions.bicarbonate(), report.notes());
    }
}
