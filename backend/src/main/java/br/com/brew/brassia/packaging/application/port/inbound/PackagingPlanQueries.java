package br.com.brew.brassia.packaging.application.port.inbound;

import br.com.brew.brassia.packaging.domain.PackagingPlan;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Consultas de planos de envase (PKG-001). */
public interface PackagingPlanQueries {

    List<PackagingPlan> list(UUID breweryId, UUID batchId);

    Optional<PackagingPlan> find(UUID breweryId, UUID planId);
}
