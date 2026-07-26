package br.com.brew.brassia.planning.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderSnapshotTest {

    private static OrderSnapshot.Equipment equipment() {
        return new OrderSnapshot.Equipment(UUID.randomUUID(), new BigDecimal("500"), new BigDecimal("20"),
                new BigDecimal("72"), new BigDecimal("8"));
    }

    @Test
    void rejectsIncompleteMetrics() {
        // Sem métricas calculadas (ogSg nulo) → snapshot incompleto.
        assertThatThrownBy(() -> new OrderSnapshot.Recipe(UUID.randomUUID(), 1, "IPA", null,
                new BigDecimal("1.010"), new BigDecimal("5"), new BigDecimal("40"), new BigDecimal("12")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("snapshot incompleto");
    }

    @Test
    void rejectsMissingCapacity() {
        assertThatThrownBy(() -> new OrderSnapshot.Equipment(UUID.randomUUID(), null, new BigDecimal("20"),
                new BigDecimal("72"), new BigDecimal("8")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullParts() {
        assertThatThrownBy(() -> new OrderSnapshot(null, equipment()))
                .isInstanceOf(NullPointerException.class);
    }
}
