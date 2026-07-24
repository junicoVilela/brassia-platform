package br.com.brew.brassia.referencedata.adapter.inbound.web.dto;

import br.com.brew.brassia.referencedata.application.port.inbound.ListStyleSetsUseCase;
import java.time.Instant;
import java.util.UUID;

public record StyleSetResponse(
        UUID id,
        boolean global,
        String authority,
        String edition,
        String language,
        String permissionStatus,
        String status,
        Instant publishedAt) {

    public static StyleSetResponse from(ListStyleSetsUseCase.SetView v) {
        return new StyleSetResponse(v.id(), v.global(), v.authority(), v.edition(), v.language(), v.permissionStatus(),
                v.status(), v.publishedAt());
    }
}
