package br.com.brew.brassia.optimization.application.port.outbound;

import br.com.brew.brassia.optimization.domain.OptimizationRun;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OptimizationRunRepository {

    void insert(OptimizationRun run);

    /** Só explicação e aplicação mudam. Entrada, método e candidatas são o registro da corrida. */
    void updateAnnotations(OptimizationRun run);

    Optional<OptimizationRun> find(UUID breweryId, UUID runId);

    Optional<OptimizationRun> findForUpdate(UUID breweryId, UUID runId);

    List<OptimizationRun> list(UUID breweryId, UUID recipeId);
}
