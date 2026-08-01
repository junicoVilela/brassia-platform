package br.com.brew.brassia.fermentation.adapter.inbound.web.dto;

import br.com.brew.brassia.fermentation.domain.FermentationSchedule;
import br.com.brew.brassia.fermentation.domain.ReschedulePreview;
import br.com.brew.brassia.fermentation.domain.ScheduleStep;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** DTOs da agenda de fermentação (FER-004), agrupados por pertencerem ao mesmo recurso. */
public final class ScheduleDtos {

    private ScheduleDtos() {
    }

    public record PlanScheduleRequest(
            @NotNull UUID profileId,
            @NotNull Instant start,
            @NotNull UUID responsibleUserId,
            Integer defaultDurationDays,
            Integer toleranceHours) {}

    public record AddStepRequest(
            @NotBlank String name,
            @NotBlank String action,
            @NotBlank String condition,
            Integer conditionDays,
            BigDecimal targetGravity,
            @NotNull Instant plannedStart,
            @NotNull Instant plannedEnd,
            @PositiveOrZero int toleranceHours,
            @NotNull UUID responsibleUserId,
            boolean dependsOnPrevious) {}

    /** {@code apply=false} devolve só a prévia; nada é gravado. */
    public record RescheduleRequest(@NotNull Instant newStart, boolean apply) {}

    public record ExecuteStepRequest(@NotNull Instant executedAt, String justification) {}

    public record StepView(UUID id, int sequence, String name, String action, String condition, Integer conditionDays,
            BigDecimal targetGravity, Instant plannedStart, Instant plannedEnd, int toleranceHours,
            UUID responsibleUserId, boolean dependsOnPrevious, String status, Instant executedAt,
            long deviationHours, String justification) {

        public static StepView from(ScheduleStep s) {
            return new StepView(s.id(), s.sequence(), s.name(), s.action().name(), s.condition().name(),
                    s.conditionDays(), s.targetGravity(), s.plannedStart(), s.plannedEnd(), s.toleranceHours(),
                    s.responsibleUserId(), s.dependsOnPrevious(), s.status().name(), s.executedAt(),
                    s.deviationHours(), s.justification());
        }
    }

    public record ScheduleView(UUID id, UUID batchId, UUID profileId, int profileVersion, List<StepView> steps) {

        public static ScheduleView from(FermentationSchedule s) {
            return new ScheduleView(s.id(), s.batchId(), s.profileId(), s.profileVersion(),
                    s.ordered().stream().map(StepView::from).toList());
        }
    }

    /** Prévia do recálculo: o que se move, de quando para quando, e o que ficou de fora. */
    public record ReschedulePreviewView(long deltaHours, List<ChangeView> changes, List<BlockedView> blocked) {

        public record ChangeView(UUID stepId, int sequence, String name, Instant fromStart, Instant toStart,
                Instant fromEnd, Instant toEnd) {

            static ChangeView from(ReschedulePreview.Change c) {
                return new ChangeView(c.stepId(), c.sequence(), c.name(), c.fromStart(), c.toStart(), c.fromEnd(),
                        c.toEnd());
            }
        }

        public record BlockedView(UUID stepId, int sequence, String name, String reason) {

            static BlockedView from(ReschedulePreview.Blocked b) {
                return new BlockedView(b.stepId(), b.sequence(), b.name(), b.reason());
            }
        }

        public static ReschedulePreviewView from(ReschedulePreview p) {
            return new ReschedulePreviewView(p.deltaHours(),
                    p.changes().stream().map(ChangeView::from).toList(),
                    p.blocked().stream().map(BlockedView::from).toList());
        }
    }
}
