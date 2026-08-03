package br.com.brew.brassia.metrology.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.metrology.application.port.inbound.InstrumentCommands;
import br.com.brew.brassia.metrology.application.port.outbound.CalibrationStandardRepository;
import br.com.brew.brassia.metrology.application.port.outbound.InstrumentRepository;
import br.com.brew.brassia.metrology.domain.CalibrationResult;
import br.com.brew.brassia.metrology.domain.CurvePoint;
import br.com.brew.brassia.metrology.domain.Instrument;
import br.com.brew.brassia.metrology.domain.InstrumentType;
import br.com.brew.brassia.metrology.domain.MeasurementRange;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Ciclo de vida do instrumento (MTR-001). Todo comando que muda a aptidão — calibrar, bloquear,
 * designar para ponto crítico, baixar — é auditado com o resultado, porque é essa cadeia que
 * explica, meses depois, por que uma medição valeu.
 */
public final class InstrumentHandlers {

    private InstrumentHandlers() {
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    private static MeasurementRange range(java.math.BigDecimal min, java.math.BigDecimal max,
            java.math.BigDecimal resolution, java.math.BigDecimal accuracy, String unit) {
        return new MeasurementRange(min, max, resolution, accuracy, unit);
    }

    private static Instrument require(InstrumentRepository instruments, UUID breweryId, UUID id) {
        return instruments.lockById(breweryId, id)
                .orElseThrow(() -> new IllegalArgumentException("instrumento inexistente"));
    }

    public static final class Register implements InstrumentCommands.Register {

        private final InstrumentRepository instruments;
        private final AuditTrail audit;

        public Register(InstrumentRepository instruments, AuditTrail audit) {
            this.instruments = Objects.requireNonNull(instruments);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public UUID handle(Command command) {
            if (instruments.existsByCode(command.breweryId(), command.code())) {
                throw new IllegalStateException("já existe instrumento com o código " + command.code());
            }
            var instrument = Instrument.register(command.breweryId(), command.code(), command.name(),
                    InstrumentType.valueOf(command.type()),
                    range(command.rangeMin(), command.rangeMax(), command.resolution(), command.accuracy(),
                            command.unit()),
                    command.location());
            instruments.insert(instrument);

            audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "metrology.instrument.register",
                    "metrology.instrument", instrument.id().toString(),
                    Map.of("code", instrument.code(), "type", instrument.type().name(),
                            "unit", instrument.range().unit())));
            return instrument.id();
        }
    }

    public static final class Amend implements InstrumentCommands.Amend {

        private final InstrumentRepository instruments;
        private final AuditTrail audit;

        public Amend(InstrumentRepository instruments, AuditTrail audit) {
            this.instruments = Objects.requireNonNull(instruments);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public void handle(Command command) {
            var instrument = require(instruments, command.breweryId(), command.instrumentId());
            instrument.amend(command.name(),
                    range(command.rangeMin(), command.rangeMax(), command.resolution(), command.accuracy(),
                            command.unit()),
                    command.location(), today());
            instruments.update(instrument);

            audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "metrology.instrument.amend",
                    "metrology.instrument", instrument.id().toString(),
                    Map.of("code", instrument.code())));
        }
    }

    public static final class SetBlock implements InstrumentCommands.SetBlock {

        private final InstrumentRepository instruments;
        private final AuditTrail audit;

        public SetBlock(InstrumentRepository instruments, AuditTrail audit) {
            this.instruments = Objects.requireNonNull(instruments);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public void handle(Command command) {
            var instrument = require(instruments, command.breweryId(), command.instrumentId());
            if (command.blocked()) {
                instrument.block(command.reason());
            } else {
                instrument.unblock();
            }
            instruments.update(instrument);

            audit.record(AuditEvent.success(command.breweryId(), command.actorId(),
                    command.blocked() ? "metrology.instrument.block" : "metrology.instrument.unblock",
                    "metrology.instrument", instrument.id().toString(),
                    Map.of("code", instrument.code(), "fitness", instrument.fitness(today()).name())));
        }
    }

    public static final class Retire implements InstrumentCommands.Retire {

        private final InstrumentRepository instruments;
        private final AuditTrail audit;

        public Retire(InstrumentRepository instruments, AuditTrail audit) {
            this.instruments = Objects.requireNonNull(instruments);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public void handle(Command command) {
            var instrument = require(instruments, command.breweryId(), command.instrumentId());
            instrument.retire(command.reason());
            instruments.update(instrument);

            audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "metrology.instrument.retire",
                    "metrology.instrument", instrument.id().toString(),
                    Map.of("code", instrument.code(), "reason", command.reason())));
        }
    }

    public static final class DesignateCriticalUse implements InstrumentCommands.DesignateCriticalUse {

        private final InstrumentRepository instruments;
        private final AuditTrail audit;

        public DesignateCriticalUse(InstrumentRepository instruments, AuditTrail audit) {
            this.instruments = Objects.requireNonNull(instruments);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public void handle(Command command) {
            var instrument = require(instruments, command.breweryId(), command.instrumentId());
            instrument.designateForCriticalUse(command.criticalUse(), today());
            instruments.update(instrument);

            audit.record(AuditEvent.success(command.breweryId(), command.actorId(),
                    "metrology.instrument.critical-use", "metrology.instrument", instrument.id().toString(),
                    Map.of("code", instrument.code(), "criticalUse", String.valueOf(command.criticalUse()))));
        }
    }

    public static final class Calibrate implements InstrumentCommands.Calibrate {

        private final InstrumentRepository instruments;
        private final CalibrationStandardRepository standards;
        private final AuditTrail audit;

        public Calibrate(InstrumentRepository instruments, CalibrationStandardRepository standards,
                AuditTrail audit) {
            this.instruments = Objects.requireNonNull(instruments);
            this.standards = Objects.requireNonNull(standards);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public UUID handle(Command command) {
            var instrument = require(instruments, command.breweryId(), command.instrumentId());
            var standard = standards.findById(command.breweryId(), command.standardId())
                    .orElseThrow(() -> new IllegalArgumentException("padrão inexistente"));

            var calibration = instrument.calibrate(standard, command.performedOn(), command.dueOn(),
                    command.performedBy(), command.certificateNumber(),
                    CalibrationResult.valueOf(command.result()), command.maxDeviation(), command.restriction(),
                    command.note(),
                    command.curve() == null ? List.of()
                            : command.curve().stream()
                                    .map(p -> new CurvePoint(p.reference(), p.measured()))
                                    .toList());

            // O certificado é histórico e só entra; o instrumento é atualizado porque a última
            // calibração passou a ser esta — inclusive quando ela reprova.
            instruments.insertCalibration(calibration);
            instruments.update(instrument);

            audit.record(AuditEvent.success(command.breweryId(), command.actorId(),
                    "metrology.instrument.calibrate", "metrology.calibration", calibration.id().toString(),
                    Map.of("instrument", instrument.code(), "standard", standard.code(),
                            "result", calibration.result().name(), "dueOn", calibration.dueOn().toString(),
                            "fitness", instrument.fitness(today()).name())));
            return calibration.id();
        }
    }
}
