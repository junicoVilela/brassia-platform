package br.com.brew.brassia.security.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Cria/reativa um mapeamento de grupo SCIM (SEC-B05). */
public record CreateScimMappingRequest(
        @NotBlank @Size(max = 500) String externalGroupId,
        @NotNull UUID securityGroupId) {}
