package br.com.brew.brassia.security.application.port.outbound;

import br.com.brew.brassia.security.application.port.inbound.AccessCatalogQuery.GroupView;
import br.com.brew.brassia.security.application.port.inbound.AccessCatalogQuery.MembershipView;
import br.com.brew.brassia.security.application.port.inbound.AccessCatalogQuery.PermissionView;
import java.util.List;
import java.util.UUID;

/** Leitura do catálogo RBAC (permissões e grupos com suas permissões). */
public interface SecurityCatalogRepository {
    List<PermissionView> listPermissions();
    List<GroupView> listGroups();

    List<MembershipView> listMembershipsByUser(UUID breweryId, UUID userId);
}
