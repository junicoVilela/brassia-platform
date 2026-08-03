package br.com.brew.brassia.metrology.application.port.inbound;

import br.com.brew.brassia.metrology.domain.Calibration;
import br.com.brew.brassia.metrology.domain.CalibrationStandard;
import br.com.brew.brassia.metrology.domain.Instrument;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Consultas de metrologia (MTR-001). */
public interface MetrologyQueries {

    List<Instrument> instruments(UUID breweryId);

    Optional<Instrument> instrument(UUID breweryId, UUID instrumentId);

    /** Histórico completo, do mais recente para o mais antigo: certificado permanece. */
    List<Calibration> calibrations(UUID breweryId, UUID instrumentId);

    List<CalibrationStandard> standards(UUID breweryId);
}
