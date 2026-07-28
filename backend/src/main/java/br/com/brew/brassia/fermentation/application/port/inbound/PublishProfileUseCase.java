package br.com.brew.brassia.fermentation.application.port.inbound;

import java.util.UUID;

/** Publica um perfil (FER-001): DRAFT → PUBLISHED (congela a versão). */
public interface PublishProfileUseCase {
    void handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID profileId) {}
}
