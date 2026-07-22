package br.com.brew.brassia.security.adapter.inbound.web.dto;

import br.com.brew.brassia.security.application.port.inbound.ListUsersUseCase;
import java.util.UUID;

public record UserSummaryResponse(
        UUID id, String email, String displayName, String status, String emailVerifiedAt) {

    public static UserSummaryResponse from(ListUsersUseCase.Summary s) {
        return new UserSummaryResponse(
                s.id(), s.email(), s.displayName(), s.status(),
                s.emailVerifiedAt() == null ? null : s.emailVerifiedAt().toString());
    }
}
