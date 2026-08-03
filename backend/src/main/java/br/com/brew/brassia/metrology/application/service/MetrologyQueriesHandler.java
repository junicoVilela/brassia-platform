package br.com.brew.brassia.metrology.application.service;

import br.com.brew.brassia.metrology.InstrumentStatusLookup;
import br.com.brew.brassia.metrology.application.port.inbound.MetrologyQueries;
import br.com.brew.brassia.metrology.application.port.outbound.CalibrationStandardRepository;
import br.com.brew.brassia.metrology.application.port.outbound.InstrumentRepository;
import br.com.brew.brassia.metrology.domain.Calibration;
import br.com.brew.brassia.metrology.domain.CalibrationStandard;
import br.com.brew.brassia.metrology.domain.Instrument;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Consultas de metrologia e a porta publicada de aptidão.
 *
 * <p>A aptidão é calculada na data pedida, nunca lida de coluna: guardar "apto" no banco criaria
 * um valor que envelhece sozinho e passa a mentir no dia seguinte ao vencimento.
 */
public final class MetrologyQueriesHandler implements MetrologyQueries, InstrumentStatusLookup {

    private final InstrumentRepository instruments;
    private final CalibrationStandardRepository standards;

    public MetrologyQueriesHandler(InstrumentRepository instruments, CalibrationStandardRepository standards) {
        this.instruments = Objects.requireNonNull(instruments);
        this.standards = Objects.requireNonNull(standards);
    }

    @Override
    public List<Instrument> instruments(UUID breweryId) {
        return instruments.findAll(breweryId);
    }

    @Override
    public Optional<Instrument> instrument(UUID breweryId, UUID instrumentId) {
        return instruments.findById(breweryId, instrumentId);
    }

    @Override
    public List<Calibration> calibrations(UUID breweryId, UUID instrumentId) {
        return instruments.findCalibrations(breweryId, instrumentId);
    }

    @Override
    public List<CalibrationStandard> standards(UUID breweryId) {
        return standards.findAll(breweryId);
    }

    @Override
    public Optional<Status> status(UUID breweryId, UUID instrumentId, LocalDate on) {
        return instruments.findById(breweryId, instrumentId)
                .map(i -> new Status(i.id(), i.code(), i.name(), i.fitness(on).name(), i.criticalUse(),
                        i.fitForCriticalUse(on), i.calibrationDueOn().orElse(null),
                        i.lastCalibration().map(Calibration::restriction).orElse(null)));
    }
}
