package br.com.brew.brassia.water.adapter.inbound.web.dto;

import br.com.brew.brassia.water.application.port.inbound.ListWaterProfilesUseCase;
import br.com.brew.brassia.water.application.port.inbound.RegisterWaterProfileUseCase;
import br.com.brew.brassia.water.application.port.inbound.UpdateWaterProfileUseCase;
import java.math.BigDecimal;
import java.util.UUID;

public record WaterProfileResponse(
        UUID id,
        String code,
        String name,
        BigDecimal calcium,
        BigDecimal magnesium,
        BigDecimal sodium,
        BigDecimal sulfate,
        BigDecimal chloride,
        BigDecimal bicarbonate,
        boolean active,
        long version) {

    public static WaterProfileResponse from(RegisterWaterProfileUseCase.Result r) {
        return new WaterProfileResponse(r.id(), r.code(), r.name(), r.calcium(), r.magnesium(), r.sodium(),
                r.sulfate(), r.chloride(), r.bicarbonate(), r.active(), r.version());
    }

    public static WaterProfileResponse from(UpdateWaterProfileUseCase.Result r) {
        return new WaterProfileResponse(r.id(), r.code(), r.name(), r.calcium(), r.magnesium(), r.sodium(),
                r.sulfate(), r.chloride(), r.bicarbonate(), r.active(), r.version());
    }

    public static WaterProfileResponse from(ListWaterProfilesUseCase.Summary s) {
        return new WaterProfileResponse(s.id(), s.code(), s.name(), s.calcium(), s.magnesium(), s.sodium(),
                s.sulfate(), s.chloride(), s.bicarbonate(), s.active(), s.version());
    }
}
