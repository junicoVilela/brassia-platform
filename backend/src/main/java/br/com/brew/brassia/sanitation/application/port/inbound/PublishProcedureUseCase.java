package br.com.brew.brassia.sanitation.application.port.inbound;

import java.util.UUID;

/** Publica um POP (CLN-001): DRAFT → PUBLISHED (congela a versão). */
public interface PublishProcedureUseCase {
    void handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID procedureId) {}
}
