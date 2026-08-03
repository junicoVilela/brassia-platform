package br.com.brew.brassia.metrology.application.port.outbound;

import br.com.brew.brassia.metrology.domain.CalibrationStandard;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CalibrationStandardRepository {

    void insert(CalibrationStandard standard);

    void update(CalibrationStandard standard);

    Optional<CalibrationStandard> findById(UUID breweryId, UUID standardId);

    Optional<CalibrationStandard> lockById(UUID breweryId, UUID standardId);

    List<CalibrationStandard> findAll(UUID breweryId);

    boolean existsByCode(UUID breweryId, String code);
}
