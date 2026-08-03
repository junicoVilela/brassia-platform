package br.com.brew.brassia.quality.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.quality.application.port.inbound.ControlPlanCommands;
import br.com.brew.brassia.quality.application.port.outbound.ControlPlanRepository;
import br.com.brew.brassia.quality.domain.ControlPlan;
import br.com.brew.brassia.quality.domain.ControlPoint;
import br.com.brew.brassia.quality.domain.Frequency;
import br.com.brew.brassia.quality.domain.FrequencyKind;
import br.com.brew.brassia.quality.domain.ProcessStage;
import br.com.brew.brassia.quality.domain.Severity;
import br.com.brew.brassia.quality.domain.SpecLimits;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Ciclo de vida do plano de controle (QLT-001): rascunho, pontos, publicação e nova versão. */
public final class ControlPlanHandlers {

    private ControlPlanHandlers() {
    }

    private static ControlPlan require(ControlPlanRepository plans, UUID breweryId, UUID planId) {
        return plans.lockById(breweryId, planId)
                .orElseThrow(() -> new IllegalArgumentException("plano de controle inexistente"));
    }

    public static final class Create implements ControlPlanCommands.Create {

        private final ControlPlanRepository plans;
        private final AuditTrail audit;

        public Create(ControlPlanRepository plans, AuditTrail audit) {
            this.plans = Objects.requireNonNull(plans);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public UUID handle(Command command) {
            if (plans.existsByCodeAndVersion(command.breweryId(), command.code(), 1)) {
                throw new IllegalStateException("já existe plano com o código " + command.code());
            }
            var plan = ControlPlan.draft(command.breweryId(), command.code(), command.name(),
                    command.recipeId(), ProcessStage.valueOf(command.stage()));
            plans.insert(plan);

            audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "quality.plan.create",
                    "quality.control_plan", plan.id().toString(),
                    Map.of("code", plan.code(), "stage", plan.stage().name())));
            return plan.id();
        }
    }

    public static final class Amend implements ControlPlanCommands.Amend {

        private final ControlPlanRepository plans;
        private final AuditTrail audit;

        public Amend(ControlPlanRepository plans, AuditTrail audit) {
            this.plans = Objects.requireNonNull(plans);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public void handle(Command command) {
            var plan = require(plans, command.breweryId(), command.planId());
            plan.amend(command.name(), command.recipeId(), ProcessStage.valueOf(command.stage()));
            plans.update(plan);

            audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "quality.plan.amend",
                    "quality.control_plan", plan.id().toString(), Map.of("code", plan.code())));
        }
    }

    public static final class AddPoint implements ControlPlanCommands.AddPoint {

        private final ControlPlanRepository plans;
        private final AuditTrail audit;

        public AddPoint(ControlPlanRepository plans, AuditTrail audit) {
            this.plans = Objects.requireNonNull(plans);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public UUID handle(Command command) {
            var plan = require(plans, command.breweryId(), command.planId());
            var point = ControlPoint.of(command.parameter(),
                    new SpecLimits(command.min(), command.max(), command.target(), command.unit()),
                    new Frequency(FrequencyKind.valueOf(command.frequencyKind()), command.everyHours()),
                    command.action(), Severity.valueOf(command.severity()), command.critical());
            plan.addPoint(point);
            plans.update(plan);

            audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "quality.plan.add-point",
                    "quality.control_plan", plan.id().toString(),
                    Map.of("code", plan.code(), "parameter", point.parameter(),
                            "severity", point.severity().name(),
                            "critical", String.valueOf(point.critical()))));
            return point.id();
        }
    }

    public static final class RemovePoint implements ControlPlanCommands.RemovePoint {

        private final ControlPlanRepository plans;
        private final AuditTrail audit;

        public RemovePoint(ControlPlanRepository plans, AuditTrail audit) {
            this.plans = Objects.requireNonNull(plans);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public void handle(Command command) {
            var plan = require(plans, command.breweryId(), command.planId());
            plan.removePoint(command.pointId());
            plans.update(plan);

            audit.record(AuditEvent.success(command.breweryId(), command.actorId(),
                    "quality.plan.remove-point", "quality.control_plan", plan.id().toString(),
                    Map.of("code", plan.code(), "point", command.pointId().toString())));
        }
    }

    public static final class Publish implements ControlPlanCommands.Publish {

        private final ControlPlanRepository plans;
        private final AuditTrail audit;

        public Publish(ControlPlanRepository plans, AuditTrail audit) {
            this.plans = Objects.requireNonNull(plans);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public void handle(Command command) {
            var plan = require(plans, command.breweryId(), command.planId());
            plan.publish();
            plans.update(plan);

            audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "quality.plan.publish",
                    "quality.control_plan", plan.id().toString(),
                    Map.of("code", plan.code(), "version", String.valueOf(plan.version()),
                            "points", String.valueOf(plan.points().size()))));
        }
    }

    public static final class NewVersion implements ControlPlanCommands.NewVersion {

        private final ControlPlanRepository plans;
        private final AuditTrail audit;

        public NewVersion(ControlPlanRepository plans, AuditTrail audit) {
            this.plans = Objects.requireNonNull(plans);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public UUID handle(Command command) {
            var plan = require(plans, command.breweryId(), command.planId());
            var draft = plan.newDraftVersion();
            plans.insert(draft);

            audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "quality.plan.new-version",
                    "quality.control_plan", draft.id().toString(),
                    Map.of("code", draft.code(), "version", String.valueOf(draft.version()))));
            return draft.id();
        }
    }
}
