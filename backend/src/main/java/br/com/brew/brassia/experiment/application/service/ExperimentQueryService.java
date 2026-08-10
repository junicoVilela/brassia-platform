package br.com.brew.brassia.experiment.application.service;

import br.com.brew.brassia.experiment.application.port.inbound.ExperimentQueries;
import br.com.brew.brassia.experiment.application.port.outbound.ExperimentRepository;
import br.com.brew.brassia.experiment.domain.ExperimentPlan;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ExperimentQueryService implements ExperimentQueries {

    private final ExperimentRepository experiments;

    public ExperimentQueryService(ExperimentRepository experiments) {
        this.experiments = Objects.requireNonNull(experiments, "experiments");
    }

    @Override
    public Optional<ExperimentPlan> find(UUID breweryId, UUID experimentId) {
        return experiments.find(breweryId, experimentId);
    }

    /** Sem receita, todos: o histórico de experimentos é leitura útil por si. */
    @Override
    public List<ExperimentPlan> list(UUID breweryId, UUID recipeId) {
        return recipeId == null ? experiments.listAll(breweryId) : experiments.listOf(breweryId, recipeId);
    }
}
