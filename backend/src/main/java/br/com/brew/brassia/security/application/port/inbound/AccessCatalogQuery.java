package br.com.brew.brassia.security.application.port.inbound;

import java.util.List;
import java.util.UUID;

/** Consulta o catálogo de permissões e os grupos (com suas permissões). */
public interface AccessCatalogQuery {
    List<PermissionView> permissions();
    List<GroupView> groups();

    /** Grupos ativos aos quais o usuário pertence, no escopo da cervejaria ativa. */
    List<MembershipView> membershipsByUser(UUID breweryId, UUID userId);

    record PermissionView(String domain, String code, String name, boolean critical, boolean active) {}

    record MembershipView(UUID groupId, String code, String name) {}

    record GroupView(
            UUID id,
            String code,
            String name,
            String description,
            UUID breweryId,
            boolean systemGroup,
            boolean active,
            long version,
            List<String> permissions) {}
}
