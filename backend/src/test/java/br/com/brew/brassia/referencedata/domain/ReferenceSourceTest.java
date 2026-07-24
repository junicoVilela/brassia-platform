package br.com.brew.brassia.referencedata.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReferenceSourceTest {

    private static LicenseInfo license(PermissionStatus status) {
        return new LicenseInfo("BJCP", status, "BJCP.org");
    }

    private static ReferenceSource global(PermissionStatus status) {
        return ReferenceSource.register(null, SourceType.OFFICIAL_STANDARD, "BJCP Beer 2021", "BJCP",
                "https://bjcp.org", license(status), "anual", "curadoria");
    }

    @Test
    void registersGlobalSource() {
        var source = global(PermissionStatus.LIMITED_PERMISSION);

        assertThat(source.isGlobal()).isTrue();
        assertThat(source.breweryId()).isNull();
        assertThat(source.type()).isEqualTo(SourceType.OFFICIAL_STANDARD);
        assertThat(source.permissionStatus()).isEqualTo(PermissionStatus.LIMITED_PERMISSION);
        assertThat(source.allowsPublish()).isTrue();
        assertThat(source.version()).isEqualTo(1);
    }

    @Test
    void registersBreweryScopedSource() {
        var brewery = UUID.randomUUID();
        var source = ReferenceSource.register(brewery, SourceType.MANUAL_CONTRIBUTION, "Malte local", "Cervejaria",
                null, license(PermissionStatus.GRANTED), null, null);

        assertThat(source.isGlobal()).isFalse();
        assertThat(source.breweryId()).isEqualTo(brewery);
        assertThat(source.url()).isNull();
    }

    @Test
    void requiresNameAndOwner() {
        assertThatThrownBy(() -> ReferenceSource.register(null, SourceType.OFFICIAL_STANDARD, " ", "BJCP", null,
                license(PermissionStatus.UNKNOWN), null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReferenceSource.register(null, SourceType.OFFICIAL_STANDARD, "BJCP", " ", null,
                license(PermissionStatus.UNKNOWN), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void licenseRequiresName() {
        assertThatThrownBy(() -> new LicenseInfo(" ", PermissionStatus.GRANTED, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void permissionGovernsPublishability() {
        assertThat(global(PermissionStatus.UNKNOWN).allowsPublish()).isFalse();
        assertThat(global(PermissionStatus.PENDING).allowsPublish()).isFalse();
        assertThat(global(PermissionStatus.DENIED).allowsPublish()).isFalse();
        assertThat(global(PermissionStatus.LIMITED_PERMISSION).allowsPublish()).isTrue();
        assertThat(global(PermissionStatus.GRANTED).allowsPublish()).isTrue();
    }
}
