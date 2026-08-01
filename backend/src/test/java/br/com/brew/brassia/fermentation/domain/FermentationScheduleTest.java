package br.com.brew.brassia.fermentation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FermentationScheduleTest {

    private static final UUID BREWERY = UUID.randomUUID();
    private static final UUID BATCH = UUID.randomUUID();
    private static final UUID PROFILE = UUID.randomUUID();
    private static final UUID RESPONSIBLE = UUID.randomUUID();
    private static final Instant START = Instant.parse("2026-08-01T08:00:00Z");

    private static ScheduleStep step(int sequence, int startDay, int endDay, boolean dependsOnPrevious) {
        return ScheduleStep.plan(sequence, "Etapa " + sequence, ScheduleAction.REST, AdvanceCondition.MANUAL, null,
                null, START.plus(Duration.ofDays(startDay)), START.plus(Duration.ofDays(endDay)), 12, RESPONSIBLE,
                dependsOnPrevious);
    }

    private static FermentationSchedule schedule(ScheduleStep... steps) {
        return FermentationSchedule.create(BREWERY, BATCH, PROFILE, 1, List.of(steps));
    }

    // --- derivação do perfil ---

    @Test
    void derivesWindowsFromPublishedProfileStages() {
        var profile = FermentationProfile.reconstitute(ProfileId.newId(), BREWERY, "ALE", "Ale", 1,
                ProfileStatus.PUBLISHED,
                List.of(new FermentationStage(UUID.randomUUID(), 1, "Primária", new BigDecimal("18"), null, null,
                                AdvanceCondition.TIME, 5, null, true),
                        new FermentationStage(UUID.randomUUID(), 2, "Diacetil", new BigDecimal("20"), null, null,
                                AdvanceCondition.GRAVITY, null, new BigDecimal("1.012"), true)),
                FgStabilityPolicy.defaults());

        var steps = FermentationSchedule.fromProfile(profile, START, RESPONSIBLE, 2, 12);

        assertThat(steps).hasSize(2);
        // Estágio por tempo dura os dias declarados; o por densidade cai no padrão.
        assertThat(steps.get(0).plannedEnd()).isEqualTo(START.plus(Duration.ofDays(5)));
        assertThat(steps.get(1).plannedStart()).isEqualTo(START.plus(Duration.ofDays(5)));
        assertThat(steps.get(1).plannedEnd()).isEqualTo(START.plus(Duration.ofDays(7)));
        // A primeira ancora; as seguintes encadeiam.
        assertThat(steps.get(0).dependsOnPrevious()).isFalse();
        assertThat(steps.get(1).dependsOnPrevious()).isTrue();
    }

    // --- prévia e recálculo ---

    @Test
    void previewShowsBeforeAndAfterWithoutChangingAnything() {
        var first = step(1, 0, 5, false);
        var second = step(2, 5, 7, true);
        var timeline = schedule(first, second);

        var preview = timeline.previewMove(first.id(), START.plus(Duration.ofDays(1)));

        assertThat(preview.deltaHours()).isEqualTo(24);
        assertThat(preview.changes()).hasSize(2);
        assertThat(preview.changes().getFirst().fromStart()).isEqualTo(START);
        assertThat(preview.changes().getFirst().toStart()).isEqualTo(START.plus(Duration.ofDays(1)));
        // Nada foi gravado: a agenda segue intacta.
        assertThat(timeline.step(first.id()).plannedStart()).isEqualTo(START);
        assertThat(timeline.step(second.id()).plannedStart()).isEqualTo(START.plus(Duration.ofDays(5)));
    }

    @Test
    void moveAppliesExactlyWhatThePreviewShowed() {
        var first = step(1, 0, 5, false);
        var second = step(2, 5, 7, true);
        var timeline = schedule(first, second);

        var applied = timeline.move(first.id(), START.plus(Duration.ofDays(1)));

        assertThat(applied.changes()).hasSize(2);
        assertThat(timeline.step(first.id()).plannedStart()).isEqualTo(START.plus(Duration.ofDays(1)));
        assertThat(timeline.step(second.id()).plannedStart()).isEqualTo(START.plus(Duration.ofDays(6)));
        assertThat(timeline.step(second.id()).plannedEnd()).isEqualTo(START.plus(Duration.ofDays(8)));
    }

    @Test
    void propagationStopsAtAnchoredStep() {
        var first = step(1, 0, 5, false);
        var second = step(2, 5, 7, true);
        // Envase com data combinada: âncora, não se move.
        var anchored = step(3, 7, 8, false);
        var timeline = schedule(first, second, anchored);

        var preview = timeline.previewMove(first.id(), START.plus(Duration.ofDays(2)));

        assertThat(preview.changes()).extracting(c -> c.sequence()).containsExactly(1, 2);
        assertThat(preview.blocked()).hasSize(1);
        assertThat(preview.blocked().getFirst().sequence()).isEqualTo(3);
        assertThat(preview.blocked().getFirst().reason()).contains("âncora");
    }

    @Test
    void propagationStopsAtExecutedStep() {
        var first = step(1, 0, 5, false);
        var second = step(2, 5, 7, true);
        var third = step(3, 7, 9, true);
        var timeline = schedule(first, second, third);
        timeline.execute(second.id(), START.plus(Duration.ofDays(6)), null);

        var preview = timeline.previewMove(first.id(), START.plus(Duration.ofDays(1)));

        assertThat(preview.changes()).extracting(c -> c.sequence()).containsExactly(1);
        assertThat(preview.blocked().getFirst().reason()).contains("já executada");
        // A etapa seguinte à executada também não se move: a cadeia parou.
        assertThat(timeline.step(third.id()).plannedStart()).isEqualTo(START.plus(Duration.ofDays(7)));
    }

    @Test
    void executedStepCannotBeRescheduled() {
        var first = step(1, 0, 5, false);
        var timeline = schedule(first);
        timeline.execute(first.id(), START.plus(Duration.ofDays(4)), null);

        assertThatThrownBy(() -> timeline.previewMove(first.id(), START.plus(Duration.ofDays(1))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("executada");
    }

    @Test
    void movingBackwardsIsAllowedAndPropagates() {
        var first = step(1, 5, 8, false);
        var second = step(2, 8, 10, true);
        var timeline = schedule(first, second);

        timeline.move(first.id(), START.plus(Duration.ofDays(3)));

        assertThat(timeline.step(second.id()).plannedStart()).isEqualTo(START.plus(Duration.ofDays(6)));
    }

    // --- execução, desvio e histórico ---

    @Test
    void executionKeepsThePlannedWindowAndRecordsDeviation() {
        var first = step(1, 0, 5, false);
        var timeline = schedule(first);

        timeline.execute(first.id(), START.plus(Duration.ofDays(5)).plus(Duration.ofHours(6)), "Atraso na CIP");

        var executed = timeline.step(first.id());
        // Planejado permanece; executado e justificativa convivem com ele.
        assertThat(executed.plannedStart()).isEqualTo(START);
        assertThat(executed.plannedEnd()).isEqualTo(START.plus(Duration.ofDays(5)));
        assertThat(executed.deviationHours()).isEqualTo(6);
        assertThat(executed.justification()).isEqualTo("Atraso na CIP");
        assertThat(executed.status()).isEqualTo(ScheduleStepStatus.DONE);
    }

    @Test
    void executionWithinToleranceNeedsNoJustification() {
        var first = step(1, 0, 5, false);
        var timeline = schedule(first);

        // 12h de tolerância: 10h depois da janela ainda passa sem justificativa.
        timeline.execute(first.id(), START.plus(Duration.ofDays(5)).plus(Duration.ofHours(10)), null);

        assertThat(timeline.step(first.id()).deviationHours()).isEqualTo(10);
        assertThat(timeline.step(first.id()).justification()).isNull();
    }

    @Test
    void executionOutsideToleranceRequiresJustification() {
        var first = step(1, 0, 5, false);
        var timeline = schedule(first);
        var tooLate = START.plus(Duration.ofDays(6));

        assertThatThrownBy(() -> timeline.execute(first.id(), tooLate, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("justificativa");
        assertThatThrownBy(() -> timeline.execute(first.id(), tooLate, "   "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(timeline.step(first.id()).status()).isEqualTo(ScheduleStepStatus.PLANNED);
    }

    @Test
    void executionInsideTheWindowHasNoDeviation() {
        var first = step(1, 0, 5, false);
        var timeline = schedule(first);

        timeline.execute(first.id(), START.plus(Duration.ofDays(3)), null);

        assertThat(timeline.step(first.id()).deviationHours()).isZero();
    }

    @Test
    void earlyExecutionDeviatesNegatively() {
        var first = step(1, 2, 5, false);
        var timeline = schedule(first);

        timeline.execute(first.id(), START.plus(Duration.ofDays(1)).plus(Duration.ofHours(20)), "Adiantado");

        assertThat(timeline.step(first.id()).deviationHours()).isEqualTo(-4);
    }

    @Test
    void aStepIsExecutedOnlyOnce() {
        var first = step(1, 0, 5, false);
        var timeline = schedule(first);
        timeline.execute(first.id(), START.plus(Duration.ofDays(4)), null);

        assertThatThrownBy(() -> timeline.execute(first.id(), START.plus(Duration.ofDays(4)), null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("já executada");
    }

    // --- atraso ---

    @Test
    void lateStepsAreOnlyPendingOnesPastTolerance() {
        var late = step(1, 0, 5, false);
        var pending = step(2, 5, 20, true);
        var timeline = schedule(late, pending);
        var now = START.plus(Duration.ofDays(6));

        assertThat(timeline.lateSteps(now)).extracting(s -> s.sequence()).containsExactly(1);

        // Executada não conta como atrasada, mesmo tendo estourado a janela.
        timeline.execute(late.id(), now, "Atraso na transferência");
        assertThat(timeline.lateSteps(now)).isEmpty();
    }

    @Test
    void toleranceDelaysTheLateFlag() {
        var first = ScheduleStep.plan(1, "Primária", ScheduleAction.REST, AdvanceCondition.MANUAL, null, null,
                START, START.plus(Duration.ofDays(5)), 24, RESPONSIBLE, false);
        var timeline = schedule(first);

        assertThat(timeline.lateSteps(START.plus(Duration.ofDays(5)).plus(Duration.ofHours(20)))).isEmpty();
        assertThat(timeline.lateSteps(START.plus(Duration.ofDays(6)).plus(Duration.ofHours(2)))).hasSize(1);
    }

    // --- invariantes de estrutura ---

    @Test
    void addsBatchSpecificStep() {
        var timeline = schedule(step(1, 0, 5, false));

        var dryHop = timeline.addStep("Dry hop", ScheduleAction.DRY_HOP, AdvanceCondition.MANUAL, null, null,
                START.plus(Duration.ofDays(3)), START.plus(Duration.ofDays(4)), 6, RESPONSIBLE, true);

        assertThat(dryHop.sequence()).isEqualTo(2);
        assertThat(timeline.ordered()).hasSize(2);
        assertThat(dryHop.action()).isEqualTo(ScheduleAction.DRY_HOP);
    }

    @Test
    void rejectsEmptyOrDuplicatedSequences() {
        assertThatThrownBy(() -> FermentationSchedule.create(BREWERY, BATCH, PROFILE, 1, List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ao menos uma etapa");
        assertThatThrownBy(() -> schedule(step(1, 0, 5, false), step(1, 5, 7, true)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("únicas");
    }

    @Test
    void rejectsInvertedWindowAndNegativeTolerance() {
        assertThatThrownBy(() -> ScheduleStep.plan(1, "X", ScheduleAction.REST, AdvanceCondition.MANUAL, null, null,
                START.plus(Duration.ofDays(2)), START, 12, RESPONSIBLE, false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("termina antes");
        assertThatThrownBy(() -> ScheduleStep.plan(1, "X", ScheduleAction.REST, AdvanceCondition.MANUAL, null, null,
                START, START.plus(Duration.ofDays(2)), -1, RESPONSIBLE, false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("tolerância");
    }

    @Test
    void requiresResponsibleAndValidatesTypedCondition() {
        assertThatThrownBy(() -> ScheduleStep.plan(1, "X", ScheduleAction.REST, AdvanceCondition.MANUAL, null, null,
                START, START.plus(Duration.ofDays(1)), 12, null, false))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ScheduleStep.plan(1, "X", ScheduleAction.REST, AdvanceCondition.TIME, null, null,
                START, START.plus(Duration.ofDays(1)), 12, RESPONSIBLE, false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("dias positivos");
        assertThatThrownBy(() -> ScheduleStep.plan(1, "X", ScheduleAction.REST, AdvanceCondition.MANUAL, 3, null,
                START, START.plus(Duration.ofDays(1)), 12, RESPONSIBLE, false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("manual");
    }

    @Test
    void parsesActionCaseInsensitively() {
        assertThat(ScheduleAction.of(" dry_hop ")).isEqualTo(ScheduleAction.DRY_HOP);
        assertThatThrownBy(() -> ScheduleAction.of("BOTTLING"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
