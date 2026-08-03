package br.com.brew.brassia.quality.application.port.outbound;

import br.com.brew.brassia.quality.domain.Deviation;
import br.com.brew.brassia.quality.domain.Measurement;
import java.util.List;
import java.util.UUID;

public interface MeasurementRepository {

    void insert(Measurement measurement);

    void insertDeviation(Deviation deviation);

    List<Measurement> findByPlan(UUID breweryId, UUID planId);

    List<Deviation> findOpenDeviations(UUID breweryId);
}
