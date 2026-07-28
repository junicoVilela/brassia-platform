package br.com.brew.brassia.sanitation.application.port.inbound;

import br.com.brew.brassia.sanitation.domain.CleaningCycle;
import java.util.List;
import java.util.UUID;

public interface ListCyclesUseCase {
    List<CleaningCycle> handle(UUID breweryId);
}
