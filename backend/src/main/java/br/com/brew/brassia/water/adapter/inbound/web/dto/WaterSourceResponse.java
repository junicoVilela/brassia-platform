package br.com.brew.brassia.water.adapter.inbound.web.dto;

import br.com.brew.brassia.water.application.port.inbound.ListWaterSourcesUseCase;
import br.com.brew.brassia.water.application.port.inbound.RegisterWaterSourceUseCase;
import br.com.brew.brassia.water.application.port.inbound.UpdateWaterSourceUseCase;
import java.util.UUID;

public record WaterSourceResponse(UUID id, String code, String name, boolean active, long version) {

    public static WaterSourceResponse from(RegisterWaterSourceUseCase.Result r) {
        return new WaterSourceResponse(r.id(), r.code(), r.name(), r.active(), r.version());
    }

    public static WaterSourceResponse from(UpdateWaterSourceUseCase.Result r) {
        return new WaterSourceResponse(r.id(), r.code(), r.name(), r.active(), r.version());
    }

    public static WaterSourceResponse from(ListWaterSourcesUseCase.Summary s) {
        return new WaterSourceResponse(s.id(), s.code(), s.name(), s.active(), s.version());
    }
}
