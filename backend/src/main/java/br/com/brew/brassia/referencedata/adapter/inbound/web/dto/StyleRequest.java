package br.com.brew.brassia.referencedata.adapter.inbound.web.dto;

import br.com.brew.brassia.referencedata.application.port.inbound.CreateStyleSetUseCase;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StyleRequest(
        @NotBlank @Size(max = 40) String code,
        @NotBlank @Size(max = 160) String name,
        @Size(max = 80) String family,
        @Size(max = 80) String category,
        RangeRequest og,
        RangeRequest fg,
        RangeRequest abv,
        RangeRequest ibu,
        RangeRequest color,
        @Size(max = 1000) String generalImpression,
        String detailedProfile) {

    public CreateStyleSetUseCase.StyleSpec toSpec() {
        return new CreateStyleSetUseCase.StyleSpec(code, name, family, category, RangeRequest.orNone(og),
                RangeRequest.orNone(fg), RangeRequest.orNone(abv), RangeRequest.orNone(ibu), RangeRequest.orNone(color),
                generalImpression, detailedProfile);
    }
}
