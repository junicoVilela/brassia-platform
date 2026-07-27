package br.com.brew.brassia.sanitation.application.port.inbound;

import java.util.List;
import java.util.UUID;

/** Cria um POP (CLN-001) em rascunho; nova versão se o código já foi publicado. */
public interface CreateProcedureUseCase {
    Result handle(Command command);

    record Command(UUID actorId, UUID breweryId, String code, String name, List<ProcedureStepInput> steps) {}

    record Result(UUID id, int version) {}
}
