package br.com.brew.brassia.planning.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScheduleEntryTest {

    private static final UUID BREWERY = UUID.randomUUID();
    private static final UUID RECIPE = UUID.randomUUID();
    private static final UUID EQUIPMENT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final ScheduleWindow WINDOW = new ScheduleWindow(
            Instant.parse("2026-08-01T08:00:00Z"), Instant.parse("2026-08-01T14:00:00Z"));

    @Test
    void plansEntryInPlannedStatus() {
        var entry = ScheduleEntry.plan(BREWERY, RECIPE, EQUIPMENT, USER,
                new BigDecimal("40"), new BigDecimal("50"), WINDOW);

        assertThat(entry.id()).isNotNull();
        assertThat(entry.breweryId()).isEqualTo(BREWERY);
        assertThat(entry.recipeId()).isEqualTo(RECIPE);
        assertThat(entry.equipmentId()).isEqualTo(EQUIPMENT);
        assertThat(entry.assignedUserId()).isEqualTo(USER);
        assertThat(entry.plannedVolumeLiters()).isEqualByComparingTo("40");
        assertThat(entry.window()).isEqualTo(WINDOW);
        assertThat(entry.status()).isEqualTo(ScheduleStatus.PLANNED);
        assertThat(entry.version()).isEqualTo(1);
    }

    @Test
    void volumeEqualToCapacityIsAllowed() {
        var entry = ScheduleEntry.plan(BREWERY, RECIPE, EQUIPMENT, USER,
                new BigDecimal("50"), new BigDecimal("50"), WINDOW);
        assertThat(entry.plannedVolumeLiters()).isEqualByComparingTo("50");
    }

    @Test
    void rejectsVolumeExceedingCapacity() {
        assertThatThrownBy(() -> ScheduleEntry.plan(BREWERY, RECIPE, EQUIPMENT, USER,
                new BigDecimal("60"), new BigDecimal("50"), WINDOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveVolume() {
        assertThatThrownBy(() -> ScheduleEntry.plan(BREWERY, RECIPE, EQUIPMENT, USER,
                BigDecimal.ZERO, new BigDecimal("50"), WINDOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScheduleEntry.plan(BREWERY, RECIPE, EQUIPMENT, USER,
                new BigDecimal("-1"), new BigDecimal("50"), WINDOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullCapacitySkipsCapacityCheck() {
        var entry = ScheduleEntry.plan(BREWERY, RECIPE, EQUIPMENT, USER,
                new BigDecimal("40"), null, WINDOW);
        assertThat(entry.plannedVolumeLiters()).isEqualByComparingTo("40");
    }

    @Test
    void requiresAssignedUser() {
        assertThatThrownBy(() -> ScheduleEntry.plan(BREWERY, RECIPE, EQUIPMENT, null,
                new BigDecimal("40"), new BigDecimal("50"), WINDOW))
                .isInstanceOf(NullPointerException.class);
    }
}
