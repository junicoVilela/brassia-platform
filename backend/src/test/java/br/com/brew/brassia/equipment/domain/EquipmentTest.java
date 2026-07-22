package br.com.brew.brassia.equipment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EquipmentTest {

    private static final UUID BREWERY = UUID.randomUUID();

    private Equipment register(String capacity, String deadSpace, String efficiency, String boilOff) {
        return Equipment.register(BREWERY, new EquipmentCode("bh-500"), new EquipmentName("Brewhouse 500L"),
                new BigDecimal(capacity), new BigDecimal(deadSpace), new BigDecimal(efficiency),
                new BigDecimal(boilOff));
    }

    @Test
    void registersValidProfile() {
        var equipment = register("500", "20", "72.5", "8");

        assertThat(equipment.code().value()).isEqualTo("BH-500");
        assertThat(equipment.capacityLiters()).isEqualByComparingTo("500");
        assertThat(equipment.version()).isZero();
        assertThat(equipment.active()).isTrue();
    }

    @Test
    void rejectsDeadSpaceAboveCapacity() {
        assertThatThrownBy(() -> register("500", "600", "72", "8"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceder a capacidade");
    }

    @Test
    void rejectsNonPositiveCapacityAndOutOfRangeEfficiency() {
        assertThatThrownBy(() -> register("0", "0", "72", "8")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> register("500", "20", "0", "8")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> register("500", "20", "120", "8")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeLossOrEvaporation() {
        assertThatThrownBy(() -> register("500", "-1", "72", "8")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> register("500", "20", "72", "-1")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateRevalidatesCapacityInvariant() {
        var equipment = register("500", "20", "72", "8");

        equipment.update(new EquipmentName("Brewhouse 500L v2"), new BigDecimal("480"), new BigDecimal("30"),
                new BigDecimal("70"), new BigDecimal("9"));
        assertThat(equipment.capacityLiters()).isEqualByComparingTo("480");
        assertThat(equipment.deadSpaceLiters()).isEqualByComparingTo("30");

        assertThatThrownBy(() -> equipment.update(new EquipmentName("X"), new BigDecimal("100"),
                new BigDecimal("200"), new BigDecimal("70"), new BigDecimal("9")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void snapshotCarriesAllMeasures() {
        var snap = register("500", "20", "72.5", "8").snapshot();
        assertThat(snap.capacityLiters()).isEqualByComparingTo("500");
        assertThat(snap.mashEfficiencyPercent()).isEqualByComparingTo("72.5");
        assertThat(snap.version()).isZero();
    }
}
