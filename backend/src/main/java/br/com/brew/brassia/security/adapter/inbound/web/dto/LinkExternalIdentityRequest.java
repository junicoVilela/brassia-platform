package br.com.brew.brassia.security.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record LinkExternalIdentityRequest(
        @NotNull UUID userId,
        @NotBlank String externalSubject,
        String normalizedEmail) {}
