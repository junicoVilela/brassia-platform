package br.com.brew.brassia.security.adapter.inbound.web.dto;

import br.com.brew.brassia.security.application.port.inbound.ManageServiceAccountUseCase;
import java.util.List;
import java.util.UUID;

public record ServiceAccountResponse(
        UUID id, UUID breweryId, String code, boolean active, List<String> credentialPrefixes) {

    public static ServiceAccountResponse from(ManageServiceAccountUseCase.ServiceAccountView view) {
        return new ServiceAccountResponse(view.id(), null, view.code(), view.active(), List.of());
    }
}
