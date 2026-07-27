package br.com.brew.brassia.sanitation.application.port.inbound;

import java.util.List;
import java.util.UUID;

/** Atualiza um POP em rascunho (CLN-001); publicado é imutável. */
public interface UpdateProcedureUseCase {
    void handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID procedureId, String name, List<ProcedureStepInput> steps) {}
}
