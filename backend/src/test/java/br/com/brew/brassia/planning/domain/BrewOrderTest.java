package br.com.brew.brassia.planning.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BrewOrderTest {

    private static final UUID BREWERY = UUID.randomUUID();
    private static final UUID RECIPE = UUID.randomUUID();

    private static OrderSnapshot snapshot(String capacity) {
        return new OrderSnapshot(
                new OrderSnapshot.Recipe(RECIPE, 1, "IPA", new BigDecimal("1.050"), new BigDecimal("1.010"),
                        new BigDecimal("5.2"), new BigDecimal("40"), new BigDecimal("12")),
                new OrderSnapshot.Equipment(UUID.randomUUID(), new BigDecimal(capacity), new BigDecimal("20"),
                        new BigDecimal("72"), new BigDecimal("8")));
    }

    @Test
    void createsInDraftWithCodeAndSnapshot() {
        var order = BrewOrder.create(BREWERY, "OP-2026-0001", RECIPE, 1, new BigDecimal("400"), snapshot("500"));

        assertThat(order.id()).isNotNull();
        assertThat(order.code()).isEqualTo("OP-2026-0001");
        assertThat(order.status()).isEqualTo(BrewOrderStatus.DRAFT);
        assertThat(order.version()).isEqualTo(1);
        assertThat(order.recipeVersion()).isEqualTo(1);
        assertThat(order.snapshot().recipe().abv()).isEqualByComparingTo("5.2");
    }

    @Test
    void volumeEqualToCapacityIsAllowed() {
        var order = BrewOrder.create(BREWERY, "OP-1", RECIPE, 1, new BigDecimal("500"), snapshot("500"));
        assertThat(order.volumeLiters()).isEqualByComparingTo("500");
    }

    @Test
    void rejectsVolumeAboveCapacity() {
        assertThatThrownBy(() -> BrewOrder.create(BREWERY, "OP-1", RECIPE, 1, new BigDecimal("600"), snapshot("500")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveVolumeAndBlankCode() {
        assertThatThrownBy(() -> BrewOrder.create(BREWERY, "OP-1", RECIPE, 1, BigDecimal.ZERO, snapshot("500")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BrewOrder.create(BREWERY, " ", RECIPE, 1, new BigDecimal("400"), snapshot("500")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
