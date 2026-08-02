package br.com.brew.brassia.gas.application.port.inbound;

import br.com.brew.brassia.gas.domain.LineBalance;
import br.com.brew.brassia.gas.domain.LineResistance;
import br.com.brew.brassia.gas.domain.ServiceLine;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Linha de serviço e balanceamento (GAS-002). */
public final class ServiceLineCommands {

    private ServiceLineCommands() {
    }

    public interface RegisterLine {
        UUID handle(Command command);

        record Command(UUID actorId, UUID breweryId, String code, String name, UUID pointOfUseEquipmentId) {}
    }

    /**
     * Calcula o balanceamento e explica; nada é aplicado nem ajustado. A pressão de serviço sai do
     * equilíbrio de carbonatação na temperatura informada.
     */
    public interface Balance {
        LineBalance handle(Query query);

        record Query(UUID breweryId, UUID lineId, BigDecimal targetCo2Volumes, BigDecimal servingTempC,
                BigDecimal elevationMeters, BigDecimal residualPressureBar, BigDecimal targetFlowLpm,
                UUID resistanceId) {}
    }

    /** Aplica a montagem à linha: gera uma revisão nova e preserva a anterior. */
    public interface ApplyRevision {
        Result handle(Command command);

        record Command(UUID actorId, UUID breweryId, UUID lineId, BigDecimal targetCo2Volumes,
                BigDecimal servingTempC, BigDecimal elevationMeters, BigDecimal residualPressureBar,
                BigDecimal targetFlowLpm, UUID resistanceId, BigDecimal appliedLengthMeters, String note) {}

        record Result(int revision, BigDecimal recommendedLengthMeters, BigDecimal lengthDeviationMeters) {}
    }

    public interface Queries {
        List<ServiceLine> lines(UUID breweryId);

        Optional<Detail> line(UUID breweryId, UUID lineId);

        List<LineResistance> tubing(UUID breweryId);

        /** Linha com o histórico de montagens; o que foi montado ontem explica o copo de ontem. */
        record Detail(ServiceLine line, List<ServiceLine.Revision> revisions) {}
    }

    /** Catálogo de tubos: os números vêm da ficha do fabricante. */
    public interface RegisterTubing {
        UUID handle(Command command);

        record Command(UUID actorId, UUID breweryId, String material, BigDecimal internalDiameterMm,
                BigDecimal resistanceBarPerMeter, BigDecimal referenceFlowLpm) {}
    }
}
