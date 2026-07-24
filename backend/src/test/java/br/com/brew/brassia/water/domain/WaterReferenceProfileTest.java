package br.com.brew.brassia.water.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WaterReferenceProfileTest {

    private static IonProfile ions() {
        return new IonProfile(new BigDecimal("50"), new BigDecimal("10"), new BigDecimal("20"),
                new BigDecimal("60"), new BigDecimal("40"), new BigDecimal("100"));
    }

    private static WaterReferenceProfile draft(UUID breweryId) {
        return WaterReferenceProfile.draft(breweryId, "Pilsen", "Plzeň", "2026", ions(), new BigDecimal("15"),
                new BigDecimal("30"), new BigDecimal("7.2"), UUID.randomUUID(), "Estudo municipal");
    }

    @Test
    void draftsGlobalEducationalProfileWithChargeBalance() {
        var profile = draft(null);

        assertThat(profile.isGlobal()).isTrue();
        assertThat(profile.status()).isEqualTo(ReferenceProfileStatus.DRAFT);
        assertThat(profile.chargeBalance().withinTolerance()).isTrue();
    }

    @Test
    void publishesThenBlocksRepublish() {
        var profile = draft(UUID.randomUUID());
        profile.publish();
        assertThat(profile.isPublished()).isTrue();
        assertThatThrownBy(profile::publish)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("já publicado");
    }

    @Test
    void rejectsPhOutOfRange() {
        assertThatThrownBy(() -> WaterReferenceProfile.draft(null, "X", null, "2026", ions(), null, null,
                new BigDecimal("15"), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
