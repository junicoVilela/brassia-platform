package br.com.brew.brassia.experiment.application.port.outbound;

import br.com.brew.brassia.experiment.domain.ExperimentPlan;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExperimentRepository {

    void insert(ExperimentPlan plan);

    /** Grava apenas o que muda com o tempo: estado e conclusão. O plano é imutável por desenho. */
    void updateProgress(ExperimentPlan plan);

    Optional<ExperimentPlan> find(UUID breweryId, UUID experimentId);

    /** O experimento carregado para alteração, com a linha travada. */
    Optional<ExperimentPlan> findForUpdate(UUID breweryId, UUID experimentId);

    List<ExperimentPlan> listOf(UUID breweryId, UUID recipeId);

    List<ExperimentPlan> listAll(UUID breweryId);
}
