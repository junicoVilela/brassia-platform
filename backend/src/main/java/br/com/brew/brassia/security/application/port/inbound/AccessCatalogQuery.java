package br.com.brew.brassia.security.application.port.inbound;

import java.util.List;
import java.util.UUID;

/** Consulta o catálogo de permissões e os grupos (com suas permissões). */
public interface AccessCatalogQuery {
    List<PermissionView> permissions();
    List<GroupView> groups();

    record PermissionView(String domain, String code, String name, boolean critical, boolean active) {}

    record GroupView(UUID id, String code, String name, UUID breweryId, boolean systemGroup,
            boolean active, List<String> permissions) {}
}
