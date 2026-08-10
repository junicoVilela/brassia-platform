package br.com.brew.brassia.experiment.application.port.inbound;

import br.com.brew.brassia.experiment.domain.ExperimentPlan;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExperimentQueries {

    Optional<ExperimentPlan> find(UUID breweryId, UUID experimentId);

    List<ExperimentPlan> list(UUID breweryId, UUID recipeId);
}
