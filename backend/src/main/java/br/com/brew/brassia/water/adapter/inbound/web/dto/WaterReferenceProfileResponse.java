package br.com.brew.brassia.water.adapter.inbound.web.dto;

import br.com.brew.brassia.water.application.port.inbound.ListWaterReferenceProfilesUseCase;
import br.com.brew.brassia.water.domain.ChargeBalance;
import br.com.brew.brassia.water.domain.IonProfile;
import java.math.BigDecimal;
import java.util.UUID;

public record WaterReferenceProfileResponse(
        UUID id,
        boolean global,
        String name,
        String region,
        String edition,
        IonProfile ions,
        BigDecimal alkalinity,
        BigDecimal hardness,
        BigDecimal ph,
        String status,
        String sourceName,
        ChargeBalance chargeBalance) {

    public static WaterReferenceProfileResponse from(ListWaterReferenceProfilesUseCase.ProfileView v) {
        return new WaterReferenceProfileResponse(v.id(), v.global(), v.name(), v.region(), v.edition(), v.ions(),
                v.alkalinity(), v.hardness(), v.ph(), v.status(), v.sourceName(), v.chargeBalance());
    }
}
