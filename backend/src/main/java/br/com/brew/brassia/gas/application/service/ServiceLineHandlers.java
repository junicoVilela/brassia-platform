package br.com.brew.brassia.gas.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.calculator.CalculatorEngine;
import br.com.brew.brassia.equipment.EquipmentProfileLookup;
import br.com.brew.brassia.gas.application.port.inbound.ServiceLineCommands;
import br.com.brew.brassia.gas.application.port.outbound.GasConnectionRepository;
import br.com.brew.brassia.gas.application.port.outbound.ServiceLineRepository;
import br.com.brew.brassia.gas.domain.GasConnection;
import br.com.brew.brassia.gas.domain.LineBalance;
import br.com.brew.brassia.gas.domain.LineResistance;
import br.com.brew.brassia.gas.domain.ServiceLine;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Linha de serviço e balanceamento (GAS-002).
 *
 * <p>A pressão de serviço sai do equilíbrio de carbonatação na temperatura informada, calculado
 * pelo hub (`calculator`) — a mesma fórmula da PKG-002, não uma cópia. Servir a outra pressão faria
 * o barril ganhar ou perder CO₂ ao longo do tempo.
 *
 * <p>Calcular não aplica nada. Aplicar gera uma revisão nova e preserva a anterior: a montagem de
 * ontem é a única evidência de por que a cerveja de ontem saiu como saiu.
 */
public final class ServiceLineHandlers {

    private static final String FORCED = "forced-carbonation-pressure";
    private static final String BALANCE = "line-balance";
    private static final String COLUMN = "beer-column-pressure";

    private ServiceLineHandlers() {
    }

    public static final class RegisterLine implements ServiceLineCommands.RegisterLine {

        private final ServiceLineRepository lines;
        private final EquipmentProfileLookup equipment;
        private final AuditTrail audit;

        public RegisterLine(ServiceLineRepository lines, EquipmentProfileLookup equipment, AuditTrail audit) {
            this.lines = Objects.requireNonNull(lines);
            this.equipment = Objects.requireNonNull(equipment);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public UUID handle(Command command) {
            if (equipment.find(command.breweryId(), command.pointOfUseEquipmentId()).isEmpty()) {
                throw new IllegalArgumentException("ponto de uso inexistente");
            }
            if (lines.existsByCode(command.breweryId(), command.code())) {
                throw new IllegalStateException("já existe linha com o código " + command.code());
            }
            var line = ServiceLine.register(command.breweryId(), command.code(), command.name(),
                    command.pointOfUseEquipmentId());
            lines.insert(line);

            audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "gas.service-line.register",
                    "gas.service-line", line.id().toString(),
                    Map.of("code", line.code(), "pointOfUse", line.pointOfUseEquipmentId().toString())));
            return line.id();
        }
    }

    public static final class RegisterTubing implements ServiceLineCommands.RegisterTubing {

        private final ServiceLineRepository lines;
        private final AuditTrail audit;

        public RegisterTubing(ServiceLineRepository lines, AuditTrail audit) {
            this.lines = Objects.requireNonNull(lines);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public UUID handle(Command command) {
            // Material e diâmetro são a identidade do tubo: recadastrar só atualiza os números.
            var existing = lines.findResistanceBySpec(command.breweryId(), command.material().trim(),
                    command.internalDiameterMm());
            if (existing.isPresent()) {
                var tubing = existing.get();
                var version = tubing.version();
                tubing.update(command.resistanceBarPerMeter(), command.referenceFlowLpm());
                if (!lines.updateResistance(tubing, version)) {
                    throw new IllegalStateException("tubo alterado por outra operação; tente novamente");
                }
                audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "gas.tubing.update",
                        "gas.tubing", tubing.id().toString(), metadata(tubing)));
                return tubing.id();
            }

            var tubing = LineResistance.register(command.breweryId(), command.material(),
                    command.internalDiameterMm(), command.resistanceBarPerMeter(), command.referenceFlowLpm());
            lines.insertResistance(tubing);
            audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "gas.tubing.register",
                    "gas.tubing", tubing.id().toString(), metadata(tubing)));
            return tubing.id();
        }

        private static Map<String, String> metadata(LineResistance tubing) {
            return Map.of("material", tubing.material(),
                    "internalDiameterMm", tubing.internalDiameterMm().toPlainString(),
                    "resistanceBarPerMeter", tubing.resistanceBarPerMeter().toPlainString());
        }
    }

    public static final class Balance implements ServiceLineCommands.Balance {

        private final ServiceLineRepository lines;
        private final GasConnectionRepository connections;
        private final CalculatorEngine calculator;

        public Balance(ServiceLineRepository lines, GasConnectionRepository connections,
                CalculatorEngine calculator) {
            this.lines = Objects.requireNonNull(lines);
            this.connections = Objects.requireNonNull(connections);
            this.calculator = Objects.requireNonNull(calculator);
        }

        @Override
        public LineBalance handle(Query query) {
            var line = lines.findById(query.breweryId(), query.lineId())
                    .orElseThrow(() -> new IllegalArgumentException("linha de serviço inexistente"));
            var tubing = lines.findResistance(query.breweryId(), query.resistanceId())
                    .orElseThrow(() -> new IllegalArgumentException("tubo inexistente no catálogo"));
            return compute(calculator, connections, line, tubing, query.targetCo2Volumes(), query.servingTempC(),
                    query.elevationMeters(), query.residualPressureBar(), query.targetFlowLpm());
        }
    }

    public static final class ApplyRevision implements ServiceLineCommands.ApplyRevision {

        private final ServiceLineRepository lines;
        private final GasConnectionRepository connections;
        private final CalculatorEngine calculator;
        private final AuditTrail audit;

        public ApplyRevision(ServiceLineRepository lines, GasConnectionRepository connections,
                CalculatorEngine calculator, AuditTrail audit) {
            this.lines = Objects.requireNonNull(lines);
            this.connections = Objects.requireNonNull(connections);
            this.calculator = Objects.requireNonNull(calculator);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public Result handle(Command command) {
            var line = lines.findForUpdate(command.breweryId(), command.lineId())
                    .orElseThrow(() -> new IllegalArgumentException("linha de serviço inexistente"));
            var tubing = lines.findResistance(command.breweryId(), command.resistanceId())
                    .orElseThrow(() -> new IllegalArgumentException("tubo inexistente no catálogo"));

            var balance = compute(calculator, connections, line, tubing, command.targetCo2Volumes(),
                    command.servingTempC(), command.elevationMeters(), command.residualPressureBar(),
                    command.targetFlowLpm());

            var version = line.version();
            var revisionNumber = line.applyRevision();
            if (!lines.update(line, version)) {
                throw new IllegalStateException("linha alterada por outra operação; tente novamente");
            }
            var revision = new ServiceLine.Revision(UUID.randomUUID(), line.id(), line.breweryId(),
                    revisionNumber, tubing.material(), tubing.internalDiameterMm(),
                    command.appliedLengthMeters(), balance.recommendedLengthMeters(),
                    balance.appliedPressureBar(), command.elevationMeters(), command.residualPressureBar(),
                    command.targetFlowLpm(), command.servingTempC(), command.targetCo2Volumes(),
                    balance.calculationMethod(), balance.calculatorVersion(), command.note(),
                    command.actorId(), Instant.now());
            lines.insertRevision(revision);

            audit.record(AuditEvent.success(line.breweryId(), command.actorId(), "gas.service-line.apply",
                    "gas.service-line", line.id().toString(),
                    Map.of("code", line.code(), "revision", String.valueOf(revisionNumber),
                            "appliedLengthMeters", revision.appliedLengthMeters().toPlainString(),
                            "recommendedLengthMeters", revision.recommendedLengthMeters().toPlainString(),
                            "appliedPressureBar", revision.appliedPressureBar().toPlainString())));

            return new Result(revisionNumber, balance.recommendedLengthMeters(),
                    revision.lengthDeviationMeters());
        }
    }

    public static final class Queries implements ServiceLineCommands.Queries {

        private final ServiceLineRepository lines;

        public Queries(ServiceLineRepository lines) {
            this.lines = Objects.requireNonNull(lines);
        }

        @Override
        public List<ServiceLine> lines(UUID breweryId) {
            return lines.findAll(breweryId);
        }

        @Override
        public Optional<Detail> line(UUID breweryId, UUID lineId) {
            return lines.findById(breweryId, lineId)
                    .map(line -> new Detail(line, lines.findRevisions(breweryId, lineId)));
        }

        @Override
        public List<LineResistance> tubing(UUID breweryId) {
            return lines.findAllResistances(breweryId);
        }
    }

    /**
     * Compõe a recomendação: a pressão vem do equilíbrio de carbonatação, o desnível vira coluna de
     * cerveja e o que sobra é o que a linha dissipa. O teto da rede de gás do ponto, quando existe,
     * entra como limite de segurança.
     */
    private static LineBalance compute(CalculatorEngine calculator, GasConnectionRepository connections,
            ServiceLine line, LineResistance tubing, BigDecimal targetCo2Volumes, BigDecimal servingTempC,
            BigDecimal elevationMeters, BigDecimal residualPressureBar, BigDecimal targetFlowLpm) {
        Objects.requireNonNull(targetCo2Volumes, "volumes de CO₂ alvo é obrigatório");
        Objects.requireNonNull(servingTempC, "temperatura de serviço é obrigatória");
        Objects.requireNonNull(elevationMeters, "desnível é obrigatório");
        Objects.requireNonNull(residualPressureBar, "pressão residual é obrigatória");
        Objects.requireNonNull(targetFlowLpm, "vazão alvo é obrigatória");

        var pressure = calculator.compute(FORCED,
                Map.of("targetVolumes", targetCo2Volumes, "tempC", servingTempC));
        var column = calculator.compute(COLUMN, Map.of("elevationMeters", elevationMeters));
        var balance = calculator.compute(BALANCE, Map.of(
                "appliedPressureBar", pressure.value(),
                "elevationMeters", elevationMeters,
                "residualPressureBar", residualPressureBar,
                "resistanceBarPerMeter", tubing.resistanceBarPerMeter(),
                "targetFlowLpm", targetFlowLpm,
                "referenceFlowLpm", tubing.referenceFlowLpm()));

        var networkMax = connections
                .findOpenConnectionAtPoint(line.breweryId(), line.pointOfUseEquipmentId())
                .map(GasConnection::networkMaxPressureBar)
                .orElse(null);
        var effectiveResistance = tubing.resistanceBarPerMeter()
                .multiply(targetFlowLpm)
                .divide(tubing.referenceFlowLpm(), 6, java.math.RoundingMode.HALF_UP);

        var alerts = new java.util.ArrayList<>(pressure.alerts());
        alerts.addAll(balance.alerts());
        return LineBalance.of(pressure.value(), balance.value(), column.value(), effectiveResistance,
                targetFlowLpm, servingTempC, targetCo2Volumes, tubing, networkMax, balance.method(),
                balance.version(), List.copyOf(alerts));
    }
}
