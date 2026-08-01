package br.com.brew.brassia.fermentation.application.port.inbound;

import br.com.brew.brassia.fermentation.domain.FermentationSchedule;
import java.util.UUID;

public interface GetScheduleUseCase {
    FermentationSchedule handle(UUID breweryId, UUID batchId);
}
