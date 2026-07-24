package br.com.brew.brassia.referencedata.adapter.inbound.web.dto;

import br.com.brew.brassia.referencedata.application.port.inbound.StyleSetDetailUseCase;
import java.util.List;
import java.util.UUID;

public record StyleSetDetailResponse(
        UUID id,
        boolean global,
        String authority,
        String edition,
        String language,
        String permissionStatus,
        String status,
        List<StyleResponse> styles) {

    public static StyleSetDetailResponse from(StyleSetDetailUseCase.Detail d) {
        return new StyleSetDetailResponse(d.id(), d.global(), d.authority(), d.edition(), d.language(),
                d.permissionStatus(), d.status(), d.styles().stream().map(StyleResponse::from).toList());
    }
}
