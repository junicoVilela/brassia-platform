package br.com.brew.brassia.optimization.application.port.inbound;

import br.com.brew.brassia.optimization.domain.OptimizationRun;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OptimizationQueries {

    Optional<OptimizationRun> find(UUID breweryId, UUID runId);

    List<OptimizationRun> list(UUID breweryId, UUID recipeId);
}
