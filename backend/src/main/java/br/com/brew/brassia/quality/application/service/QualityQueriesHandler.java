package br.com.brew.brassia.quality.application.service;

import br.com.brew.brassia.quality.application.port.inbound.QualityQueries;
import br.com.brew.brassia.quality.application.port.outbound.ControlPlanRepository;
import br.com.brew.brassia.quality.application.port.outbound.MeasurementRepository;
import br.com.brew.brassia.quality.application.port.outbound.NonConformityRepository;
import br.com.brew.brassia.quality.domain.ControlPlan;
import br.com.brew.brassia.quality.domain.Deviation;
import br.com.brew.brassia.quality.domain.Measurement;
import br.com.brew.brassia.quality.domain.NonConformity;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class QualityQueriesHandler implements QualityQueries {

    private final ControlPlanRepository plans;
    private final MeasurementRepository measurements;
    private final NonConformityRepository nonConformities;

    public QualityQueriesHandler(ControlPlanRepository plans, MeasurementRepository measurements,
            NonConformityRepository nonConformities) {
        this.plans = Objects.requireNonNull(plans);
        this.measurements = Objects.requireNonNull(measurements);
        this.nonConformities = Objects.requireNonNull(nonConformities);
    }

    @Override
    public List<ControlPlan> plans(UUID breweryId) {
        return plans.findAll(breweryId);
    }

    @Override
    public Optional<ControlPlan> plan(UUID breweryId, UUID planId) {
        return plans.findById(breweryId, planId);
    }

    @Override
    public List<Measurement> measurements(UUID breweryId, UUID planId) {
        return measurements.findByPlan(breweryId, planId);
    }

    @Override
    public List<Deviation> deviations(UUID breweryId) {
        return measurements.findOpenDeviations(breweryId);
    }

    @Override
    public List<NonConformity> nonConformities(UUID breweryId) {
        return nonConformities.findAll(breweryId);
    }

    @Override
    public Optional<NonConformity> nonConformity(UUID breweryId, UUID nonConformityId) {
        return nonConformities.findById(breweryId, nonConformityId);
    }
}
