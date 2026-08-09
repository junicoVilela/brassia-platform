package br.com.brew.brassia.experiment.application.port.inbound;

import br.com.brew.brassia.experiment.domain.ExperimentPlan;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Planejar e conduzir um lote dividido (EXP-001). */
public interface ExperimentCommands {

    ExperimentPlan plan(PlanCommand command);

    ExperimentPlan start(UUID breweryId, UUID experimentId, UUID actor);

    ExperimentPlan conclude(ConcludeCommand command);

    ExperimentPlan abandon(UUID breweryId, UUID experimentId, UUID actor);

    /**
     * @param factors todos os fatores comparados — inclusive os iguais. Ver ExperimentFactor: sem os
     *                iguais declarados, ninguém confere depois que o resto ficou mesmo igual.
     */
    record PlanCommand(UUID breweryId, UUID recipeId, String hypothesis, UUID controlBatchId,
            UUID variantBatchId, List<FactorInput> factors, Set<String> plannedMeasurements,
            boolean sensoryPlanned, boolean sensoryBlind, UUID actor) {
    }

    record FactorInput(String name, String controlValue, String variantValue) {
    }

    record ConcludeCommand(UUID breweryId, UUID experimentId, boolean supported, String observation,
            UUID actor) {
    }
}
