package br.com.brew.brassia.referencedata.adapter.inbound.web.dto;

import br.com.brew.brassia.referencedata.application.port.inbound.StyleSetDetailUseCase;
import br.com.brew.brassia.referencedata.domain.StyleRange;

public record StyleResponse(
        String code,
        String name,
        String family,
        String category,
        StyleRange og,
        StyleRange fg,
        StyleRange abv,
        StyleRange ibu,
        StyleRange color,
        String generalImpression,
        boolean hasDetailedProfile) {

    public static StyleResponse from(StyleSetDetailUseCase.StyleView v) {
        return new StyleResponse(v.code(), v.name(), v.family(), v.category(), v.og(), v.fg(), v.abv(), v.ibu(),
                v.color(), v.generalImpression(), v.hasDetailedProfile());
    }
}
