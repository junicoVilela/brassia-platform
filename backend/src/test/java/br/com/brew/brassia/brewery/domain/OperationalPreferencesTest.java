package br.com.brew.brassia.brewery.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OperationalPreferencesTest {

    @Test
    void updateBumpsVersionAndSnapshotKeepsValues() {
        var prefs = OperationalPreferences.defaults(UUID.randomUUID());
        var before = prefs.snapshot();

        prefs.update("ML", "G", "F", "USD", new BigDecimal("50"), true, "FIFO", 0);

        assertThat(prefs.version()).isEqualTo(1);
        assertThat(prefs.volumeUnit()).isEqualTo("ML");
        assertThat(before.preferenceVersion()).isEqualTo(0);
        assertThat(before.volumeUnit()).isEqualTo("L");
        assertThat(prefs.snapshot().volumeUnit()).isEqualTo("ML");
    }

    @Test
    void staleVersionConflicts() {
        var prefs = OperationalPreferences.defaults(UUID.randomUUID());
        assertThatThrownBy(() -> prefs.update("L", "KG", "C", "BRL", new BigDecimal("10"), false, "FEFO", 3))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsInvalidUnit() {
        var prefs = OperationalPreferences.defaults(UUID.randomUUID());
        assertThatThrownBy(() -> prefs.update("GAL", "KG", "C", "BRL", new BigDecimal("10"), false, "FEFO", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
