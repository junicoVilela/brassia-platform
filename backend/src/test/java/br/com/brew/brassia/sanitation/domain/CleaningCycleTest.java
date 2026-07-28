package br.com.brew.brassia.sanitation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CleaningCycleTest {

    private static final UUID BREWERY = UUID.randomUUID();
    private static final UUID EQUIPMENT = UUID.randomUUID();

    private static StepExecution ok() {
        return new StepExecution(new BigDecimal("2.0"), new BigDecimal("60"), 20, "CIP", null, null, false, null);
    }

    private static CleaningProcedure published(List<ProcedureStep> steps) {
        return CleaningProcedure.reconstitute(ProcedureId.newId(), BREWERY, "CIP-TANK", "CIP de tanque", 1,
                ProcedureStatus.PUBLISHED, steps);
    }

    private static ProcedureStep step(int seq, boolean evidence) {
        return ProcedureStep.of(seq, "CIP", "soda", new BigDecimal("1.0"), new BigDecimal("3.0"),
                new BigDecimal("50"), new BigDecimal("70"), 15, "recirculação", "luvas", null, null, evidence);
    }

    @Test
    void startSnapshotsPublishedStepsInProgress() {
        var cycle = CleaningCycle.start(BREWERY, published(List.of(step(1, false), step(2, false))), EQUIPMENT);
        assertThat(cycle.status()).isEqualTo(CleaningCycleStatus.IN_PROGRESS);
        assertThat(cycle.steps()).hasSize(2);
        assertThat(cycle.steps().getFirst().status()).isEqualTo(CycleStepStatus.PENDING);
        assertThat(cycle.procedureVersion()).isEqualTo(1);
    }

    @Test
    void rejectsUnpublishedProcedure() {
        var draft = CleaningProcedure.draft(BREWERY, "CIP-TANK", "rascunho", 1, List.of(step(1, false)));
        assertThatThrownBy(() -> CleaningCycle.start(BREWERY, draft, EQUIPMENT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recordsStepWithinRange() {
        var cycle = CleaningCycle.start(BREWERY, published(List.of(step(1, false))), EQUIPMENT);
        cycle.recordStep(1, ok());
        assertThat(cycle.steps().getFirst().status()).isEqualTo(CycleStepStatus.DONE);
        assertThat(cycle.steps().getFirst().overridden()).isFalse();
    }

    @Test
    void blocksParameterOutOfSpec() {
        var cycle = CleaningCycle.start(BREWERY, published(List.of(step(1, false))), EQUIPMENT);
        var tooHot = new StepExecution(new BigDecimal("2.0"), new BigDecimal("95"), 20, null, null, null, false, null);
        assertThatThrownBy(() -> cycle.recordStep(1, tooHot))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fora da ficha");
    }

    @Test
    void blocksTimeBelowMinimumDwell() {
        var cycle = CleaningCycle.start(BREWERY, published(List.of(step(1, false))), EQUIPMENT);
        var tooShort = new StepExecution(new BigDecimal("2.0"), new BigDecimal("60"), 5, null, null, null, false, null);
        assertThatThrownBy(() -> cycle.recordStep(1, tooShort))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fora da ficha");
    }

    @Test
    void overrideBypassesRangeButRequiresReason() {
        var cycle = CleaningCycle.start(BREWERY, published(List.of(step(1, false))), EQUIPMENT);
        var forcedNoReason = new StepExecution(
                new BigDecimal("2.0"), new BigDecimal("95"), 20, null, null, null, true, null);
        assertThatThrownBy(() -> cycle.recordStep(1, forcedNoReason))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("justificativa");

        var forced = new StepExecution(
                new BigDecimal("2.0"), new BigDecimal("95"), 20, null, null, null, true, "linha nova, validado");
        cycle.recordStep(1, forced);
        assertThat(cycle.steps().getFirst().overridden()).isTrue();
        assertThat(cycle.steps().getFirst().overrideReason()).isEqualTo("linha nova, validado");
    }

    @Test
    void outOfOrderRequiresReason() {
        var cycle = CleaningCycle.start(BREWERY, published(List.of(step(1, false), step(2, false))), EQUIPMENT);
        assertThatThrownBy(() -> cycle.recordStep(2, ok()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("fora de ordem");

        var withReason = new StepExecution(
                new BigDecimal("2.0"), new BigDecimal("60"), 20, null, null, "operador priorizou etapa 2", false, null);
        cycle.recordStep(2, withReason);
        assertThat(cycle.steps().get(1).status()).isEqualTo(CycleStepStatus.DONE);
    }

    @Test
    void evidenceRequiredMustBeProvided() {
        var cycle = CleaningCycle.start(BREWERY, published(List.of(step(1, true))), EQUIPMENT);
        assertThatThrownBy(() -> cycle.recordStep(1, ok()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("evidência");
    }

    @Test
    void reRecordingDoneStepFails() {
        var cycle = CleaningCycle.start(BREWERY, published(List.of(step(1, false))), EQUIPMENT);
        cycle.recordStep(1, ok());
        assertThatThrownBy(() -> cycle.recordStep(1, ok())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void interruptIsPreservedAndResumable() {
        var cycle = CleaningCycle.start(BREWERY, published(List.of(step(1, false))), EQUIPMENT);
        cycle.interrupt("falta de químico");
        assertThat(cycle.status()).isEqualTo(CleaningCycleStatus.INTERRUPTED);
        assertThat(cycle.interruptReason()).isEqualTo("falta de químico");
        assertThatThrownBy(() -> cycle.recordStep(1, ok())).isInstanceOf(IllegalStateException.class);
        cycle.resume();
        assertThat(cycle.status()).isEqualTo(CleaningCycleStatus.IN_PROGRESS);
        assertThat(cycle.interruptReason()).isEqualTo("falta de químico");
    }

    @Test
    void completeRequiresAllStepsDone() {
        var cycle = CleaningCycle.start(BREWERY, published(List.of(step(1, false), step(2, false))), EQUIPMENT);
        cycle.recordStep(1, ok());
        assertThatThrownBy(cycle::complete).isInstanceOf(IllegalStateException.class);
        cycle.recordStep(2, ok());
        cycle.complete();
        assertThat(cycle.status()).isEqualTo(CleaningCycleStatus.COMPLETED);
        assertThat(cycle.endedAt()).isNotNull();
    }

    private static CleaningCycle completed() {
        var cycle = CleaningCycle.start(BREWERY, published(List.of(step(1, false))), EQUIPMENT);
        cycle.recordStep(1, ok());
        cycle.complete();
        return cycle;
    }

    @Test
    void verificationRequiresCompletedCycle() {
        var inProgress = CleaningCycle.start(BREWERY, published(List.of(step(1, false))), EQUIPMENT);
        assertThatThrownBy(() -> inProgress.recordVerification(true, true, new BigDecimal("50"),
                new BigDecimal("100"), true)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void releasesWhenAllChecksPass() {
        var cycle = completed();
        cycle.recordVerification(true, true, new BigDecimal("40"), new BigDecimal("100"), true);
        assertThat(cycle.verification().passed()).isTrue();
        cycle.release();
        assertThat(cycle.status()).isEqualTo(CleaningCycleStatus.RELEASED);
        assertThat(cycle.decidedAt()).isNotNull();
    }

    @Test
    void doesNotReleaseWithFailedCheckOrHighAtp() {
        var cycle = completed();
        // ATP acima do limite reprova.
        cycle.recordVerification(true, true, new BigDecimal("150"), new BigDecimal("100"), true);
        assertThat(cycle.verification().atpOk()).isFalse();
        assertThat(cycle.verification().passed()).isFalse();
        assertThatThrownBy(cycle::release).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("reprovada");
        // Reprovar leva a REJECTED.
        cycle.reject();
        assertThat(cycle.status()).isEqualTo(CleaningCycleStatus.REJECTED);
    }

    @Test
    void releaseRequiresVerificationFirst() {
        var cycle = completed();
        assertThatThrownBy(cycle::release).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(cycle::reject).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void consumptionRequiresEndedExecution() {
        var inProgress = CleaningCycle.start(BREWERY, published(List.of(step(1, false))), EQUIPMENT);
        assertThatThrownBy(() -> inProgress.recordConsumption(new BigDecimal("100"), new BigDecimal("5"),
                new BigDecimal("2"))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void recordsAndUpsertsConsumptionOnEndedCycle() {
        var cycle = completed();
        cycle.recordConsumption(new BigDecimal("120"), new BigDecimal("6"), new BigDecimal("2.5"));
        assertThat(cycle.consumption().waterLiters()).isEqualByComparingTo("120");
        // Re-registro sobrescreve.
        cycle.recordConsumption(new BigDecimal("110"), new BigDecimal("5.5"), new BigDecimal("2.0"));
        assertThat(cycle.consumption().waterLiters()).isEqualByComparingTo("110");
        assertThat(cycle.consumption().energyKwh()).isEqualByComparingTo("5.5");
    }

    @Test
    void rejectsNegativeConsumption() {
        var cycle = completed();
        assertThatThrownBy(() -> cycle.recordConsumption(new BigDecimal("-1"), new BigDecimal("5"),
                new BigDecimal("2"))).isInstanceOf(IllegalArgumentException.class);
    }
}
