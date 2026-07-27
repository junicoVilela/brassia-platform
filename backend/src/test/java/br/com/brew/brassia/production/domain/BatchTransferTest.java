package br.com.brew.brassia.production.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BatchTransferTest {

    private static BatchTransfer transfer(String volume, String losses) {
        return BatchTransfer.record(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal(volume), new BigDecimal("1.052"), losses == null ? null : new BigDecimal(losses),
                Instant.now(), UUID.randomUUID());
    }

    @Test
    void recordsAndDefaultsLossesToZero() {
        var t = transfer("390", null);
        assertThat(t.volumeLiters()).isEqualByComparingTo("390");
        assertThat(t.lossesLiters()).isEqualByComparingTo("0");
    }

    @Test
    void rejectsNonPositiveVolume() {
        assertThatThrownBy(() -> transfer("0", "0")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeLosses() {
        assertThatThrownBy(() -> transfer("390", "-1")).isInstanceOf(IllegalArgumentException.class);
    }
}
