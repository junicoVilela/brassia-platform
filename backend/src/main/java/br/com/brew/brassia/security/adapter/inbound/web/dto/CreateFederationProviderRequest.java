package br.com.brew.brassia.security.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record CreateFederationProviderRequest(
        @NotBlank @Size(max = 80) String code,
        @NotBlank @Size(max = 160) String displayName,
        @NotBlank @Pattern(regexp = "SAML|OIDC") String protocol,
        @NotBlank String issuerOrEntityId,
        Map<String, Object> configuration) {}
