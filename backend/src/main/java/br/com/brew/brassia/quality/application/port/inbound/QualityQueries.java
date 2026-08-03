package br.com.brew.brassia.quality.application.port.inbound;

import br.com.brew.brassia.quality.domain.ControlPlan;
import br.com.brew.brassia.quality.domain.Deviation;
import br.com.brew.brassia.quality.domain.Measurement;
import br.com.brew.brassia.quality.domain.NonConformity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Consultas de qualidade (QLT-001). */
public interface QualityQueries {

    List<ControlPlan> plans(UUID breweryId);

    Optional<ControlPlan> plan(UUID breweryId, UUID planId);

    List<Measurement> measurements(UUID breweryId, UUID planId);

    /** Desvios abertos, dos mais severos e recentes primeiro. */
    List<Deviation> deviations(UUID breweryId);

    List<NonConformity> nonConformities(UUID breweryId);

    Optional<NonConformity> nonConformity(UUID breweryId, UUID nonConformityId);
}
