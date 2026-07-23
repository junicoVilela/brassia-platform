package br.com.brew.brassia.water.adapter.inbound.web.dto;

import br.com.brew.brassia.water.application.port.inbound.SimulateWaterBlendUseCase;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record BlendResultResponse(
        String method,
        BigDecimal totalVolumeLiters,
        BigDecimal calcium,
        BigDecimal magnesium,
        BigDecimal sodium,
        BigDecimal sulfate,
        BigDecimal chloride,
        BigDecimal bicarbonate,
        List<AppliedInput> inputs,
        Target target) {

    public record AppliedInput(UUID sourceId, String code, BigDecimal volumeLiters) {}

    public record Deviation(BigDecimal calcium, BigDecimal magnesium, BigDecimal sodium, BigDecimal sulfate,
            BigDecimal chloride, BigDecimal bicarbonate) {}

    public record Target(UUID profileId, String code, Deviation deviation) {}

    public static BlendResultResponse from(SimulateWaterBlendUseCase.Result r) {
        var inputs = r.inputs().stream()
                .map(i -> new AppliedInput(i.sourceId(), i.code(), i.volumeLiters()))
                .toList();
        Target target = null;
        if (r.target() != null) {
            var d = r.target().deviation();
            target = new Target(r.target().profileId(), r.target().code(),
                    new Deviation(d.calcium(), d.magnesium(), d.sodium(), d.sulfate(), d.chloride(),
                            d.bicarbonate()));
        }
        return new BlendResultResponse(r.method(), r.totalVolumeLiters(), r.calcium(), r.magnesium(),
                r.sodium(), r.sulfate(), r.chloride(), r.bicarbonate(), inputs, target);
    }
}
