package br.com.brew.brassia.security.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MfaLoginRequest(
        @NotBlank String code,
        @NotBlank @Pattern(regexp = "TOTP|RECOVERY_CODE") String method) {}
