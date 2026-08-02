package br.com.brew.brassia.packaging.application.port.inbound;

import java.util.UUID;

/** Confirma um item do checklist de envase (PKG-001); repetir é inócuo. */
public interface ConfirmChecklistItemUseCase {
    void handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID planId, String item) {}
}
