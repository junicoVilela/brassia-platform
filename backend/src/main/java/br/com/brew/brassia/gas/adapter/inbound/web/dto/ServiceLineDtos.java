package br.com.brew.brassia.gas.adapter.inbound.web.dto;

import br.com.brew.brassia.gas.application.port.inbound.ServiceLineCommands;
import br.com.brew.brassia.gas.domain.LineBalance;
import br.com.brew.brassia.gas.domain.LineResistance;
import br.com.brew.brassia.gas.domain.ServiceLine;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Contratos da linha de serviço e do balanceamento (GAS-002). */
public final class ServiceLineDtos {

    private ServiceLineDtos() {
    }

    public record RegisterLineRequest(
            @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 120) String name,
            @NotNull UUID pointOfUseEquipmentId) {}

    public record RegisterTubingRequest(
            @NotBlank @Size(max = 60) String material,
            @NotNull @Positive BigDecimal internalDiameterMm,
            @NotNull @Positive BigDecimal resistanceBarPerMeter,
            @NotNull @Positive BigDecimal referenceFlowLpm) {}

    /** O desnível pode ser negativo: a torneira pode ficar abaixo do barril. */
    public record ApplyRevisionRequest(
            @NotNull @Positive BigDecimal targetCo2Volumes,
            @NotNull BigDecimal servingTempC,
            @NotNull BigDecimal elevationMeters,
            @NotNull @PositiveOrZero BigDecimal residualPressureBar,
            @NotNull @Positive BigDecimal targetFlowLpm,
            @NotNull UUID resistanceId,
            @NotNull @Positive BigDecimal appliedLengthMeters,
            @Size(max = 200) String note) {}

    public record ServiceLineView(UUID id, String code, String name, UUID pointOfUseEquipmentId,
            int currentRevision, boolean everApplied) {

        public static ServiceLineView from(ServiceLine line) {
            return new ServiceLineView(line.id(), line.code(), line.name(), line.pointOfUseEquipmentId(),
                    line.currentRevision(), line.everApplied());
        }
    }

    public record RevisionView(int revision, String material, BigDecimal internalDiameterMm,
            BigDecimal appliedLengthMeters, BigDecimal recommendedLengthMeters,
            BigDecimal lengthDeviationMeters, BigDecimal appliedPressureBar, BigDecimal elevationMeters,
            BigDecimal residualPressureBar, BigDecimal targetFlowLpm, BigDecimal servingTempC,
            BigDecimal targetCo2Volumes, String calculationMethod, String calculatorVersion, String note,
            UUID appliedBy, Instant appliedAt) {

        static RevisionView from(ServiceLine.Revision r) {
            return new RevisionView(r.revision(), r.material(), r.internalDiameterMm(),
                    r.appliedLengthMeters(), r.recommendedLengthMeters(), r.lengthDeviationMeters(),
                    r.appliedPressureBar(), r.elevationMeters(), r.residualPressureBar(), r.targetFlowLpm(),
                    r.servingTempC(), r.targetCo2Volumes(), r.calculationMethod(), r.calculatorVersion(),
                    r.note(), r.appliedBy(), r.appliedAt());
        }
    }

    public record ServiceLineDetailView(ServiceLineView line, List<RevisionView> revisions) {

        public static ServiceLineDetailView from(ServiceLineCommands.Queries.Detail detail) {
            return new ServiceLineDetailView(ServiceLineView.from(detail.line()),
                    detail.revisions().stream().map(RevisionView::from).toList());
        }
    }

    public record TubingView(UUID id, String material, BigDecimal internalDiameterMm,
            BigDecimal resistanceBarPerMeter, BigDecimal referenceFlowLpm) {

        public static TubingView from(LineResistance r) {
            return new TubingView(r.id(), r.material(), r.internalDiameterMm(), r.resistanceBarPerMeter(),
                    r.referenceFlowLpm());
        }
    }

    /** Recomendação com o método, os limites e os avisos de segurança. */
    public record LineBalanceView(BigDecimal appliedPressureBar, BigDecimal recommendedLengthMeters,
            BigDecimal hydrostaticBar, BigDecimal effectiveResistanceBarPerMeter, BigDecimal targetFlowLpm,
            BigDecimal servingTempC, BigDecimal targetCo2Volumes, String material,
            BigDecimal internalDiameterMm, String calculationMethod, String calculatorVersion,
            boolean feasible, List<WarningView> warnings) {

        public record WarningView(String code, String message, boolean safety) {}

        public static LineBalanceView from(LineBalance b) {
            return new LineBalanceView(b.appliedPressureBar(), b.recommendedLengthMeters(), b.hydrostaticBar(),
                    b.effectiveResistanceBarPerMeter(), b.targetFlowLpm(), b.servingTempC(),
                    b.targetCo2Volumes(), b.material(), b.internalDiameterMm(), b.calculationMethod(),
                    b.calculatorVersion(), b.feasible(),
                    b.warnings().stream()
                            .map(w -> new WarningView(w.code(), w.message(), w.safety()))
                            .toList());
        }
    }
}
