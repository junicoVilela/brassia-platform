package br.com.brew.brassia.quality.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.metrology.InstrumentStatusLookup;
import br.com.brew.brassia.production.BatchAlertPublisher;
import br.com.brew.brassia.quality.application.port.inbound.MeasurementCommands;
import br.com.brew.brassia.quality.application.port.outbound.ControlPlanRepository;
import br.com.brew.brassia.quality.application.port.outbound.MeasurementRepository;
import br.com.brew.brassia.quality.domain.ControlPoint;
import br.com.brew.brassia.quality.domain.CriticalPointInstrumentException;
import br.com.brew.brassia.quality.domain.Deviation;
import br.com.brew.brassia.quality.domain.Measurement;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Registro de medição contra o plano (QLT-001).
 *
 * <p>Três coisas acontecem num commit só: julgar o valor contra a faixa, gravar a medição e — se
 * saiu da faixa — abrir o desvio. Separar abriria a porta para uma medição fora da faixa existir
 * sem o desvio correspondente, que é exatamente o estado que a história existe para impedir.
 *
 * <p><strong>Ponto crítico exige instrumento apto.</strong> É aqui que a designação criada em
 * MTR-001 vira regra executável, consultando a porta publicada {@code InstrumentStatusLookup} — a
 * verificação acontece no momento da medição, que é quando ela importa. Em ponto não crítico a
 * medição passa mesmo com instrumento vencido, mas passa <em>dizendo</em> que passou assim.
 */
public final class MeasurementHandler implements MeasurementCommands.Record {

    private final ControlPlanRepository plans;
    private final MeasurementRepository measurements;
    private final InstrumentStatusLookup instruments;
    private final BatchAlertPublisher alerts;
    private final AuditTrail audit;

    public MeasurementHandler(ControlPlanRepository plans, MeasurementRepository measurements,
            InstrumentStatusLookup instruments, BatchAlertPublisher alerts, AuditTrail audit) {
        this.plans = Objects.requireNonNull(plans);
        this.measurements = Objects.requireNonNull(measurements);
        this.instruments = Objects.requireNonNull(instruments);
        this.alerts = Objects.requireNonNull(alerts);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Outcome handle(Command command) {
        var plan = plans.findById(command.breweryId(), command.planId())
                .orElseThrow(() -> new IllegalArgumentException("plano de controle inexistente"));
        var point = plan.point(command.pointId())
                .orElseThrow(() -> new IllegalArgumentException("ponto inexistente no plano"));

        var fitness = checkInstrument(command, point);
        // `judge` recusa rascunho: só plano publicado produz veredito.
        var violation = plan.judge(point.id(), command.value());

        var measurement = Measurement.record(command.breweryId(), plan, point, command.batchId(),
                command.instrumentId(), fitness, command.value(), violation.isEmpty(), command.note(),
                command.measuredAt(), command.actorId());
        measurements.insert(measurement);

        UUID deviationId = null;
        if (violation.isPresent()) {
            var deviation = Deviation.open(command.breweryId(), measurement.id(), plan, point,
                    violation.orElseThrow(), command.value(), command.measuredAt(), command.actorId());
            measurements.insertDeviation(deviation);
            deviationId = deviation.id();
            notifyBatch(command, deviation);
        }

        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "quality.measurement.record",
                "quality.measurement", measurement.id().toString(),
                Map.of("plan", plan.code(), "parameter", point.parameter(),
                        "value", command.value().toPlainString(),
                        "withinSpec", String.valueOf(measurement.withinSpec()),
                        "severity", violation.isPresent() ? point.severity().name() : "-")));
        return new Outcome(measurement.id(), measurement.withinSpec(), deviationId);
    }

    /**
     * @return aptidão do instrumento no momento, ou {@code null} quando a medição não declarou
     *     instrumento — nem toda medição usa um (contagem, inspeção visual)
     */
    private String checkInstrument(Command command, ControlPoint point) {
        if (command.instrumentId() == null) {
            if (point.critical()) {
                throw new CriticalPointInstrumentException(point.parameter(), "(nenhum)", "SEM_INSTRUMENTO");
            }
            return null;
        }
        var status = instruments.status(command.breweryId(), command.instrumentId(),
                        LocalDate.now(ZoneOffset.UTC))
                .orElseThrow(() -> new IllegalArgumentException("instrumento inexistente"));
        if (point.critical() && !"FIT".equals(status.fitness())) {
            throw new CriticalPointInstrumentException(point.parameter(), status.code(), status.fitness());
        }
        return status.fitness();
    }

    /**
     * Desvio grave sinaliza na central do lote (PRD-006), como a FER-004 faz com etapa atrasada —
     * em vez de manter uma segunda central. Alerta é aviso: não muda o estado do lote.
     */
    private void notifyBatch(Command command, Deviation deviation) {
        if (command.batchId() == null || !deviation.severity().alertsBatch()) {
            return;
        }
        alerts.openStepAlert(command.breweryId(), command.actorId(), command.batchId(),
                "Desvio %s: %s. Ação: %s".formatted(deviation.severity().label(), deviation.describe(),
                        deviation.action()),
                command.measuredAt(), command.measuredAt());
    }
}
