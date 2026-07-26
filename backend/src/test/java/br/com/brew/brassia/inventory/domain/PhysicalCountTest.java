package br.com.brew.brassia.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PhysicalCountTest {

    private static final UUID BREWERY = UUID.randomUUID();
    private static final Instant AT = Instant.parse("2026-09-01T10:00:00Z");

    private static CountLine line(String counted, String system) {
        return new CountLine(UUID.randomUUID(), UUID.randomUUID(), StockUnit.KG,
                new BigDecimal(counted), new BigDecimal(system));
    }

    @Test
    void opensWithLinesAndComputesDifference() {
        var count = PhysicalCount.open(BREWERY, List.of(line("8", "10")), AT);
        assertThat(count.status()).isEqualTo(PhysicalCountStatus.OPEN);
        assertThat(count.lines().get(0).difference()).isEqualByComparingTo("-2");
    }

    @Test
    void rejectsEmptyOrNegativeCount() {
        assertThatThrownBy(() -> PhysicalCount.open(BREWERY, List.of(), AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CountLine(UUID.randomUUID(), UUID.randomUUID(), StockUnit.KG,
                new BigDecimal("-1"), BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void approveTransitionsOnlyFromOpen() {
        var count = PhysicalCount.open(BREWERY, List.of(line("8", "10")), AT);
        var approved = count.approve(AT);
        assertThat(approved.status()).isEqualTo(PhysicalCountStatus.APPROVED);
        assertThat(approved.approvedAt()).isEqualTo(AT);
        assertThat(approved.approvable()).isFalse();
        assertThatThrownBy(() -> approved.approve(AT)).isInstanceOf(IllegalStateException.class);
    }
}
