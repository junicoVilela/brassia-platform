package br.com.brew.brassia.optimization.application.service;

import br.com.brew.brassia.optimization.application.port.inbound.OptimizationQueries;
import br.com.brew.brassia.optimization.application.port.outbound.OptimizationRunRepository;
import br.com.brew.brassia.optimization.domain.OptimizationRun;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class OptimizationQueryService implements OptimizationQueries {

    private final OptimizationRunRepository runs;

    public OptimizationQueryService(OptimizationRunRepository runs) {
        this.runs = Objects.requireNonNull(runs, "runs");
    }

    @Override
    public Optional<OptimizationRun> find(UUID breweryId, UUID runId) {
        return runs.find(breweryId, runId);
    }

    @Override
    public List<OptimizationRun> list(UUID breweryId, UUID recipeId) {
        return runs.list(breweryId, recipeId);
    }
}
