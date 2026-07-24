package br.com.brew.brassia.referencedata.adapter.inbound.web.dto;

import br.com.brew.brassia.referencedata.application.port.inbound.ListReferenceSourcesUseCase;
import java.util.UUID;

public record ReferenceSourceResponse(
        UUID id,
        boolean global,
        String type,
        String name,
        String owner,
        String url,
        String licenseName,
        String permissionStatus,
        String attribution) {

    public static ReferenceSourceResponse from(ListReferenceSourcesUseCase.SourceView v) {
        return new ReferenceSourceResponse(v.id(), v.global(), v.type(), v.name(), v.owner(), v.url(),
                v.licenseName(), v.permissionStatus(), v.attribution());
    }
}
