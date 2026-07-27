package br.com.brew.brassia.sanitation.application.port.inbound;

import br.com.brew.brassia.sanitation.domain.CleaningProcedure;
import java.util.UUID;

public interface GetProcedureUseCase {
    CleaningProcedure handle(UUID breweryId, UUID procedureId);
}
