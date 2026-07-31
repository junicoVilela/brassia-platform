package br.com.brew.brassia.fermentation.application.port.inbound;

import br.com.brew.brassia.fermentation.domain.YeastHarvest;
import java.util.List;
import java.util.UUID;

public interface ListYeastHarvestsUseCase {
    /** {@code onlyAvailable} lista apenas as aprovadas. */
    List<YeastHarvest> handle(UUID breweryId, boolean onlyAvailable);
}
