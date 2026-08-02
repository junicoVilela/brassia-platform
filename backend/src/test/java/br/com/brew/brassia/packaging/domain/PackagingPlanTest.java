package br.com.brew.brassia.packaging.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PackagingPlanTest {

    private static final UUID BREWERY = UUID.randomUUID();
    private static final UUID BATCH = UUID.randomUUID();
    private static final UUID CONTAINER = UUID.randomUUID();
    private static final UUID LINE = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();
    private static final Instant START = Instant.parse("2026-08-05T09:00:00Z");
    private static final Instant END = Instant.parse("2026-08-05T15:00:00Z");

    private static PackagingPlan plan(int units, String containerMl, String batchLiters) {
        return PackagingPlan.plan(BREWERY, "ENV-001", BATCH, CONTAINER, new BigDecimal(containerMl), units, LINE,
                START, END, new BigDecimal(batchLiters));
    }

    private static PackagingPlan plan() {
        return plan(1000, "355", "400");
    }

    private static PackagingPlan confirmedPlan() {
        var plan = plan();
        for (var item : ChecklistItem.values()) {
            plan.confirm(item, ACTOR, START);
        }
        return plan;
    }

    @Test
    void startsPlannedWithEmptyChecklist() {
        var plan = plan();

        assertThat(plan.status()).isEqualTo(PackagingPlanStatus.PLANNED);
        assertThat(plan.pendingChecklist()).containsExactlyInAnyOrder(ChecklistItem.values());
        assertThat(plan.reservedAt()).isNull();
        assertThat(plan.active()).isTrue();
    }

    @Test
    void derivesPlannedVolumeFromUnitsAndContainerSize() {
        // 1000 × 355 ml = 355 L; volume nunca é informado, sempre derivado.
        assertThat(plan().plannedVolumeLiters()).isEqualByComparingTo("355");
        assertThat(plan(24, "500", "400").plannedVolumeLiters()).isEqualByComparingTo("12");
    }

    @Test
    void refusesToPlanMoreThanTheBatchHolds() {
        assertThatThrownBy(() -> plan(1200, "355", "400"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("excede o volume do lote");
    }

    @Test
    void acceptsPlanThatUsesTheWholeBatch() {
        assertThat(plan(800, "500", "400").plannedVolumeLiters()).isEqualByComparingTo("400");
    }

    @Test
    void rejectsNonPositiveQuantitiesAndInvertedWindow() {
        assertThatThrownBy(() -> plan(0, "355", "400")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> plan(-5, "355", "400")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> plan(10, "0", "400")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PackagingPlan.plan(BREWERY, "ENV-001", BATCH, CONTAINER, new BigDecimal("355"),
                10, LINE, END, START, new BigDecimal("400")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("posterior ao início");
    }

    @Test
    void keepsFirstConfirmationWhenItemIsConfirmedTwice() {
        var plan = plan();
        var other = UUID.randomUUID();

        plan.confirm(ChecklistItem.SEAL_TEST, ACTOR, START);
        plan.confirm(ChecklistItem.SEAL_TEST, other, END);

        var confirmation = plan.checklist().get(ChecklistItem.SEAL_TEST);
        assertThat(confirmation.actorId()).isEqualTo(ACTOR);
        assertThat(confirmation.at()).isEqualTo(START);
        assertThat(plan.pendingChecklist()).doesNotContain(ChecklistItem.SEAL_TEST);
    }

    @Test
    void refusesReserveWhileChecklistIsPending() {
        var plan = plan();
        plan.confirm(ChecklistItem.SEAL_TEST, ACTOR, START);

        assertThatThrownBy(() -> plan.reserve(ACTOR, START))
                .isInstanceOf(PackagingBlockedException.class)
                .extracting(e -> ((PackagingBlockedException) e).blockers())
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.list(
                        PackagingBlockedException.Blocker.class))
                .hasSize(2)
                .allMatch(b -> b.code().equals("checklist_pending"));
        assertThat(plan.status()).isEqualTo(PackagingPlanStatus.PLANNED);
    }

    @Test
    void reservesOnceChecklistIsComplete() {
        var plan = confirmedPlan();

        plan.reserve(ACTOR, END);

        assertThat(plan.status()).isEqualTo(PackagingPlanStatus.RESERVED);
        assertThat(plan.reservedBy()).isEqualTo(ACTOR);
        assertThat(plan.reservedAt()).isEqualTo(END);
        assertThat(plan.ownBlockers()).isEmpty();
    }

    @Test
    void refusesToReserveTwice() {
        var plan = confirmedPlan();
        plan.reserve(ACTOR, END);

        assertThatThrownBy(() -> plan.reserve(ACTOR, END))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("já reservado");
    }

    @Test
    void cancelsFromPlannedAndFromReserved() {
        var planned = plan();
        planned.cancel("lote reprovado na análise", END);
        assertThat(planned.status()).isEqualTo(PackagingPlanStatus.CANCELLED);
        assertThat(planned.active()).isFalse();

        var reserved = confirmedPlan();
        reserved.reserve(ACTOR, END);
        reserved.cancel("linha quebrou", END);
        assertThat(reserved.status()).isEqualTo(PackagingPlanStatus.CANCELLED);
    }

    @Test
    void cancellationRequiresReasonAndIsTerminal() {
        var plan = plan();
        assertThatThrownBy(() -> plan.cancel(" ", END)).isInstanceOf(IllegalArgumentException.class);

        plan.cancel("lote reprovado", END);
        assertThatThrownBy(() -> plan.cancel("de novo", END)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> plan.confirm(ChecklistItem.SEAL_TEST, ACTOR, END))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> plan.reserve(ACTOR, END)).isInstanceOf(IllegalStateException.class);
    }
}
