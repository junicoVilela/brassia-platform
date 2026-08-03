package br.com.brew.brassia.metrology.application.port.outbound;

import br.com.brew.brassia.metrology.domain.Calibration;
import br.com.brew.brassia.metrology.domain.Instrument;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InstrumentRepository {

    void insert(Instrument instrument);

    /** Grava o instrumento com trava otimista; a calibração nova é histórico e só é inserida. */
    void update(Instrument instrument);

    Optional<Instrument> findById(UUID breweryId, UUID instrumentId);

    /** Trava a linha para comandos que decidem a partir do estado atual. */
    Optional<Instrument> lockById(UUID breweryId, UUID instrumentId);

    List<Instrument> findAll(UUID breweryId);

    boolean existsByCode(UUID breweryId, String code);

    void insertCalibration(Calibration calibration);

    List<Calibration> findCalibrations(UUID breweryId, UUID instrumentId);
}
