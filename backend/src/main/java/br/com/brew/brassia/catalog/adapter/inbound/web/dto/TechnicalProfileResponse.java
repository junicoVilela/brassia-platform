package br.com.brew.brassia.catalog.adapter.inbound.web.dto;

import br.com.brew.brassia.catalog.application.port.inbound.TechnicalProfileUseCase;
import br.com.brew.brassia.catalog.domain.PropertyRange;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record TechnicalProfileResponse(
        UUID ingredientId,
        String manufacturer,
        String origin,
        String form,
        String purpose,
        String laboratory,
        String labCode,
        Map<String, PropertyRange> ranges,
        List<String> descriptors,
        UUID sourceId,
        String sourceName,
        String status) {

    public static TechnicalProfileResponse from(TechnicalProfileUseCase.ProfileView v) {
        return new TechnicalProfileResponse(v.ingredientId(), v.manufacturer(), v.origin(), v.form(), v.purpose(),
                v.laboratory(), v.labCode(), v.ranges(), v.descriptors(), v.sourceId(), v.sourceName(), v.status());
    }
}
