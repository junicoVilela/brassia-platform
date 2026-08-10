package br.com.brew.brassia.experiment.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.experiment.application.port.inbound.ExperimentCommands;
import br.com.brew.brassia.experiment.application.port.outbound.ExperimentRepository;
import br.com.brew.brassia.experiment.domain.ExperimentFactor;
import br.com.brew.brassia.experiment.domain.ExperimentPlan;
import br.com.brew.brassia.experiment.domain.InvalidExperimentSubjectException;
import br.com.brew.brassia.experiment.domain.Limitation;
import br.com.brew.brassia.experiment.domain.UnknownExperimentException;
import br.com.brew.brassia.production.BatchLookup;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Planejar e conduzir um lote dividido (EXP-001).
 *
 * <p><strong>A checagem que só existe aqui: os dois lotes têm que ser da mesma receita.</strong> O domínio
 * não pode fazê-la — ele não conhece lote nenhum, só identificadores —, e sem ela um "controle" de outra
 * receita faria a comparação medir a diferença entre duas receitas e atribuí-la ao fator isolado. É o
 * resultado errado mais convincente que este módulo pode produzir, porque parece um experimento correto.
 */
public final class ExperimentHandler implements ExperimentCommands {

    private final ExperimentRepository experiments;
    private final BatchLookup batches;
    private final AuditTrail audit;
    private final Clock clock;

    public ExperimentHandler(ExperimentRepository experiments, BatchLookup batches, AuditTrail audit,
            Clock clock) {
        this.experiments = Objects.requireNonNull(experiments, "experiments");
        this.batches = Objects.requireNonNull(batches, "batches");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public ExperimentPlan plan(PlanCommand command) {
        Objects.requireNonNull(command, "command");
        requireSameRecipe(command);

        var factors = command.factors().stream()
                .map(f -> new ExperimentFactor(f.name(), f.controlValue(), f.variantValue()))
                .toList();

        var plan = ExperimentPlan.plan(UUID.randomUUID(), command.breweryId(), command.recipeId(),
                command.hypothesis(), command.controlBatchId(), command.variantBatchId(), factors,
                command.plannedMeasurements(), command.sensoryPlanned(), command.sensoryBlind(),
                command.actor(), clock.instant());

        experiments.insert(plan);

        var metadata = new LinkedHashMap<String, String>();
        metadata.put("recipeId", plan.recipeId().toString());
        metadata.put("isolatedVariable", plan.isolatedVariable().name());
        // As limitações vão para a auditoria porque são parte do que se decidiu ao desenhar o experimento,
        // não uma observação sobre o resultado. Registrá-las só na conclusão faria parecer que apareceram
        // depois.
        metadata.put("limitations", joined(plan.limitations()));
        audit.record(AuditEvent.success(plan.breweryId(), command.actor(), "experiment.plan.create",
                "experiment_plan", plan.id().toString(), metadata));
        return plan;
    }

    @Override
    public ExperimentPlan start(UUID breweryId, UUID experimentId, UUID actor) {
        var plan = lockedOrFail(breweryId, experimentId);
        plan.start();
        experiments.updateProgress(plan);
        audit.record(AuditEvent.success(breweryId, actor, "experiment.plan.start", "experiment_plan",
                plan.id().toString(), Map.of()));
        return plan;
    }

    @Override
    public ExperimentPlan conclude(ConcludeCommand command) {
        Objects.requireNonNull(command, "command");
        var plan = lockedOrFail(command.breweryId(), command.experimentId());
        plan.conclude(command.supported(), command.observation(), command.actor(), clock.instant());
        experiments.updateProgress(plan);

        var conclusion = plan.conclusion().orElseThrow();
        var metadata = new LinkedHashMap<String, String>();
        metadata.put("supported", String.valueOf(conclusion.supported()));
        metadata.put("limitations", joined(conclusion.limitations()));
        audit.record(AuditEvent.success(command.breweryId(), command.actor(),
                "experiment.plan.conclude", "experiment_plan", plan.id().toString(), metadata));
        return plan;
    }

    @Override
    public ExperimentPlan abandon(UUID breweryId, UUID experimentId, UUID actor) {
        var plan = lockedOrFail(breweryId, experimentId);
        plan.abandon();
        experiments.updateProgress(plan);
        audit.record(AuditEvent.success(breweryId, actor, "experiment.plan.abandon", "experiment_plan",
                plan.id().toString(), Map.of()));
        return plan;
    }

    /**
     * Carrega com a linha travada.
     *
     * <p>Sem o {@code FOR UPDATE}, duas conclusões simultâneas leriam o mesmo RUNNING e ambas passariam
     * pela verificação de estado — a segunda sobrescreveria a primeira, e a conclusão registrada teria um
     * autor que não escreveu aquela observação.
     */
    private ExperimentPlan lockedOrFail(UUID breweryId, UUID experimentId) {
        return experiments.findForUpdate(breweryId, experimentId)
                .orElseThrow(() -> new UnknownExperimentException(experimentId));
    }

    private static String joined(List<Limitation> limitations) {
        return limitations.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    private void requireSameRecipe(PlanCommand command) {
        var control = batches.find(command.breweryId(), command.controlBatchId())
                .orElseThrow(() -> new InvalidExperimentSubjectException(
                        "lote de controle não existe nesta cervejaria"));
        var variant = batches.find(command.breweryId(), command.variantBatchId())
                .orElseThrow(() -> new InvalidExperimentSubjectException(
                        "lote variante não existe nesta cervejaria"));

        if (!control.recipeId().equals(command.recipeId())
                || !variant.recipeId().equals(command.recipeId())) {
            throw new InvalidExperimentSubjectException(
                    "controle e variante precisam ser da receita em experimento — comparar lotes de "
                            + "receitas diferentes atribuiria ao fator isolado uma diferença que é das "
                            + "próprias receitas");
        }
    }
}
