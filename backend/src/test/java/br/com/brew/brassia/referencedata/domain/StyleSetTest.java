package br.com.brew.brassia.referencedata.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StyleSetTest {

    private static final Instant NOW = Instant.parse("2026-07-24T00:00:00Z");

    private static Style style() {
        return Style.create("21A", "American IPA", "IPA", "21", StyleRange.none(), StyleRange.none(),
                StyleRange.none(), StyleRange.none(), StyleRange.none(), "IPA", null, PermissionStatus.LIMITED_PERMISSION);
    }

    private static StyleSet set(UUID breweryId, PermissionStatus permission) {
        return StyleSet.draft(breweryId, ReferenceSourceId.newId(), StyleAuthority.BJCP_BEER, "2021", "en", NOW, null,
                "BJCP.org", permission, List.of(style()));
    }

    @Test
    void draftsGlobalSetWithStyles() {
        var styleSet = set(null, PermissionStatus.LIMITED_PERMISSION);
        assertThat(styleSet.isGlobal()).isTrue();
        assertThat(styleSet.status()).isEqualTo(DatasetStatus.DRAFT);
        assertThat(styleSet.authority()).isEqualTo(StyleAuthority.BJCP_BEER);
        assertThat(styleSet.edition()).isEqualTo("2021");
        assertThat(styleSet.styles()).hasSize(1);
    }

    @Test
    void publishesWhenPermissionAllows() {
        var styleSet = set(UUID.randomUUID(), PermissionStatus.GRANTED);
        styleSet.publish(NOW);
        assertThat(styleSet.isPublished()).isTrue();
        assertThat(styleSet.publishedAt()).isEqualTo(NOW);
    }

    @Test
    void publishBlockedWhenPermissionDenies() {
        var styleSet = set(null, PermissionStatus.PENDING);
        assertThatThrownBy(() -> styleSet.publish(NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PENDING");
        assertThat(styleSet.status()).isEqualTo(DatasetStatus.DRAFT);
    }

    @Test
    void republishBlocked() {
        var styleSet = set(null, PermissionStatus.LIMITED_PERMISSION);
        styleSet.publish(NOW);
        assertThatThrownBy(() -> styleSet.publish(NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("já publicado");
    }
}
