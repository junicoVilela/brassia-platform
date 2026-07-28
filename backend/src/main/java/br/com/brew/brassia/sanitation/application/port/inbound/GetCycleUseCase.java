package br.com.brew.brassia.sanitation.application.port.inbound;

import br.com.brew.brassia.sanitation.domain.CleaningCycle;
import java.util.UUID;

public interface GetCycleUseCase {
    CleaningCycle handle(UUID breweryId, UUID cycleId);
}
