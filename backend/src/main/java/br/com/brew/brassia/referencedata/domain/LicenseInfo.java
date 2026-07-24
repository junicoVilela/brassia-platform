package br.com.brew.brassia.referencedata.domain;

import java.util.Objects;

/** Licença e condição de uso de uma fonte: nome, estado de permissão e atribuição. */
public record LicenseInfo(String licenseName, PermissionStatus permissionStatus, String attribution) {
    public LicenseInfo {
        licenseName = licenseName == null ? "" : licenseName.trim();
        if (licenseName.isBlank()) {
            throw new IllegalArgumentException("licenseName é obrigatório");
        }
        Objects.requireNonNull(permissionStatus, "permissionStatus é obrigatório");
        attribution = attribution == null || attribution.isBlank() ? null : attribution.trim();
    }

    public boolean allowsPublish() {
        return permissionStatus.allowsPublish();
    }
}
