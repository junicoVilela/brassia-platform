package br.com.brew.brassia.security.adapter.inbound.web.dto;

import br.com.brew.brassia.security.application.port.outbound.ScimGroupMappingRepository;
import java.util.UUID;

/** Mapeamento de grupo SCIM (externo → interno) para administração (SEC-B05). */
public record ScimMappingResponse(String externalGroupId, UUID securityGroupId, boolean active) {

    public static ScimMappingResponse from(ScimGroupMappingRepository.MappingView view) {
        return new ScimMappingResponse(view.externalGroupId(), view.securityGroupId(), view.active());
    }
}
