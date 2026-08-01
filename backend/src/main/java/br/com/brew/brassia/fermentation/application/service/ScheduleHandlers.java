package br.com.brew.brassia.fermentation.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.fermentation.application.port.inbound.AddScheduleStepUseCase;
import br.com.brew.brassia.fermentation.application.port.inbound.ExecuteScheduleStepUseCase;
import br.com.brew.brassia.fermentation.application.port.inbound.GetScheduleUseCase;
import br.com.brew.brassia.fermentation.application.port.inbound.PlanScheduleUseCase;
import br.com.brew.brassia.fermentation.application.port.inbound.RaiseLateStepAlertsUseCase;
import br.com.brew.brassia.fermentation.application.port.inbound.RescheduleStepUseCase;
import br.com.brew.brassia.fermentation.application.port.outbound.ProfileRepository;
import br.com.brew.brassia.fermentation.application.port.outbound.ScheduleRepository;
import br.com.brew.brassia.fermentation.domain.AdvanceCondition;
import br.com.brew.brassia.fermentation.domain.FermentationSchedule;
import br.com.brew.brassia.fermentation.domain.ReschedulePreview;
import br.com.brew.brassia.fermentation.domain.ScheduleAction;
import br.com.brew.brassia.production.BatchAlertPublisher;
import br.com.brew.brassia.production.BatchLookup;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Casos de uso da agenda de fermentação (FER-004). Agrupados porque compartilham a mesma
 * carga (agenda do lote) e as mesmas guardas de tenant/estado; cada um continua com sua
 * porta própria.
 */
public final class ScheduleHandlers {

    private static final int DEFAULT_DURATION_DAYS = 3;
    private static final int DEFAULT_TOLERANCE_HOURS = 12;

    private ScheduleHandlers() {
    }

    /** Cria a agenda a partir de um perfil publicado; um lote tem uma linha do tempo só. */
    public static final class Plan implements PlanScheduleUseCase {

        private final ScheduleRepository schedules;
        private final ProfileRepository profiles;
        private final BatchLookup batches;
        private final AuditTrail audit;

        public Plan(ScheduleRepository schedules, ProfileRepository profiles, BatchLookup batches, AuditTrail audit) {
            this.schedules = Objects.requireNonNull(schedules);
            this.profiles = Objects.requireNonNull(profiles);
            this.batches = Objects.requireNonNull(batches);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public Result handle(Command command) {
            if (!batches.exists(command.breweryId(), command.batchId())) {
                throw new IllegalArgumentException("lote inexistente: " + command.batchId());
            }
            if (schedules.findByBatch(command.breweryId(), command.batchId()).isPresent()) {
                throw new IllegalStateException("o lote já tem uma agenda de fermentação");
            }
            var profile = profiles.findById(command.breweryId(), command.profileId())
                    .orElseThrow(() -> new IllegalArgumentException("perfil inexistente"));
            // Rascunho ainda muda debaixo da agenda; só o publicado é congelado.
            if (profile.draftStatus()) {
                throw new IllegalStateException("perfil em rascunho não pode reger uma agenda");
            }

            var steps = FermentationSchedule.fromProfile(profile, command.start(), command.responsibleUserId(),
                    command.defaultDurationDays() == null ? DEFAULT_DURATION_DAYS : command.defaultDurationDays(),
                    command.toleranceHours() == null ? DEFAULT_TOLERANCE_HOURS : command.toleranceHours());
            var schedule = FermentationSchedule.create(command.breweryId(), command.batchId(),
                    profile.id().value(), profile.version(), steps);
            schedules.insert(schedule);

            audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "fermentation.schedule.plan",
                    "fermentation.schedule", schedule.id().toString(),
                    Map.of("batchId", command.batchId().toString(), "profileId", profile.id().value().toString(),
                            "profileVersion", String.valueOf(profile.version()),
                            "steps", String.valueOf(steps.size()))));

            return new Result(schedule.id(), steps.size());
        }
    }

    public static final class Get implements GetScheduleUseCase {

        private final ScheduleRepository schedules;

        public Get(ScheduleRepository schedules) {
            this.schedules = Objects.requireNonNull(schedules);
        }

        @Override
        public FermentationSchedule handle(UUID breweryId, UUID batchId) {
            return schedules.findByBatch(breweryId, batchId)
                    .orElseThrow(() -> new IllegalArgumentException("lote sem agenda de fermentação"));
        }
    }

    public static final class AddStep implements AddScheduleStepUseCase {

        private final ScheduleRepository schedules;
        private final AuditTrail audit;

        public AddStep(ScheduleRepository schedules, AuditTrail audit) {
            this.schedules = Objects.requireNonNull(schedules);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public UUID handle(Command command) {
            var schedule = load(schedules, command.breweryId(), command.batchId());
            var step = schedule.addStep(command.name(), ScheduleAction.of(command.action()),
                    AdvanceCondition.of(command.condition()), command.conditionDays(), command.targetGravity(),
                    command.plannedStart(), command.plannedEnd(), command.toleranceHours(),
                    command.responsibleUserId(), command.dependsOnPrevious());
            schedules.replaceSteps(schedule);

            audit.record(AuditEvent.success(command.breweryId(), command.actorId(),
                    "fermentation.schedule.step.add", "fermentation.schedule", schedule.id().toString(),
                    Map.of("stepId", step.id().toString(), "action", step.action().name())));
            return step.id();
        }
    }

    /** Prévia ou aplicação do recálculo — a mesma conta, gravando ou não. */
    public static final class Reschedule implements RescheduleStepUseCase {

        private final ScheduleRepository schedules;
        private final AuditTrail audit;

        public Reschedule(ScheduleRepository schedules, AuditTrail audit) {
            this.schedules = Objects.requireNonNull(schedules);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public ReschedulePreview handle(Command command) {
            var schedule = load(schedules, command.breweryId(), command.batchId());
            if (!command.apply()) {
                return schedule.previewMove(command.stepId(), command.newStart());
            }
            var applied = schedule.move(command.stepId(), command.newStart());
            schedules.replaceSteps(schedule);

            audit.record(AuditEvent.success(command.breweryId(), command.actorId(),
                    "fermentation.schedule.reschedule", "fermentation.schedule", schedule.id().toString(),
                    Map.of("stepId", command.stepId().toString(),
                            "deltaHours", String.valueOf(applied.deltaHours()),
                            "movedSteps", String.valueOf(applied.changes().size()))));
            return applied;
        }
    }

    public static final class Execute implements ExecuteScheduleStepUseCase {

        private final ScheduleRepository schedules;
        private final AuditTrail audit;

        public Execute(ScheduleRepository schedules, AuditTrail audit) {
            this.schedules = Objects.requireNonNull(schedules);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public void handle(Command command) {
            var schedule = load(schedules, command.breweryId(), command.batchId());
            schedule.execute(command.stepId(), command.executedAt(), command.justification());
            schedules.replaceSteps(schedule);

            var step = schedule.step(command.stepId());
            audit.record(AuditEvent.success(command.breweryId(), command.actorId(),
                    "fermentation.schedule.step.execute", "fermentation.schedule", schedule.id().toString(),
                    Map.of("stepId", step.id().toString(),
                            "deviationHours", String.valueOf(step.deviationHours()))));
        }
    }

    /** Varre atrasos e avisa na central do lote; não toca em setpoint nem no estado da etapa. */
    public static final class RaiseLateAlerts implements RaiseLateStepAlertsUseCase {

        private final ScheduleRepository schedules;
        private final BatchAlertPublisher alerts;

        public RaiseLateAlerts(ScheduleRepository schedules, BatchAlertPublisher alerts) {
            this.schedules = Objects.requireNonNull(schedules);
            this.alerts = Objects.requireNonNull(alerts);
        }

        @Override
        public List<UUID> handle(UUID actorId, UUID breweryId) {
            var now = Instant.now();
            var opened = new ArrayList<UUID>();
            for (var schedule : schedules.findWithPendingSteps(breweryId)) {
                for (var step : schedule.lateSteps(now)) {
                    opened.add(alerts.openStepAlert(breweryId, actorId, schedule.batchId(),
                            "Etapa de fermentação atrasada: " + step.name(), step.plannedEnd(), null));
                }
            }
            return opened;
        }
    }

    private static FermentationSchedule load(ScheduleRepository schedules, UUID breweryId, UUID batchId) {
        return schedules.findByBatch(breweryId, batchId)
                .orElseThrow(() -> new IllegalArgumentException("lote sem agenda de fermentação"));
    }
}
