package br.com.brew.brassia.security.adapter.inbound.web.dto;

import br.com.brew.brassia.security.application.port.inbound.ManageGroupUseCase;
import java.util.List;
import java.util.UUID;

public record GroupResponse(
        UUID id,
        String code,
        String name,
        String description,
        UUID breweryId,
        boolean systemGroup,
        boolean active,
        long version,
        List<String> permissions) {

    public static GroupResponse from(ManageGroupUseCase.Result r) {
        return new GroupResponse(
                r.id(), r.code(), r.name(), r.description(), r.breweryId(),
                r.systemGroup(), r.active(), r.version(), r.permissions());
    }
}
