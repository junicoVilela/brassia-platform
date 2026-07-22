package br.com.brew.brassia.security.adapter.inbound.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record TemporaryAccessGrantRequest(
        @NotNull UUID userId,
        @NotBlank @Size(max = 120) String permissionCode,
        @NotBlank @Size(max = 500) String reason,
        @Positive @Max(720) int durationHours) {}
