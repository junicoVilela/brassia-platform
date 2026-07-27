package br.com.brew.brassia.sanitation.application.port.inbound;

import br.com.brew.brassia.sanitation.domain.CleaningProcedure;
import java.util.List;
import java.util.UUID;

public interface ListProceduresUseCase {
    List<CleaningProcedure> handle(UUID breweryId);
}
