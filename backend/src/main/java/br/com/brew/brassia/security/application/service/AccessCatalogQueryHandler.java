package br.com.brew.brassia.security.application.service;

import br.com.brew.brassia.security.application.port.inbound.AccessCatalogQuery;
import br.com.brew.brassia.security.application.port.outbound.SecurityCatalogRepository;
import java.util.List;
import java.util.Objects;

public final class AccessCatalogQueryHandler implements AccessCatalogQuery {
    private final SecurityCatalogRepository catalog;

    public AccessCatalogQueryHandler(SecurityCatalogRepository catalog) {
        this.catalog = Objects.requireNonNull(catalog);
    }

    @Override
    public List<PermissionView> permissions() {
        return catalog.listPermissions();
    }

    @Override
    public List<GroupView> groups() {
        return catalog.listGroups();
    }
}
