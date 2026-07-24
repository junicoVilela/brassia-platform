package br.com.brew.brassia.referencedata.application.port.inbound;

import br.com.brew.brassia.referencedata.domain.PermissionStatus;
import br.com.brew.brassia.referencedata.domain.SourceType;
import java.util.UUID;

public interface RegisterReferenceSourceUseCase {

    Result handle(Command command);

    /** {@code breweryId} nulo registra uma fonte global (curadoria BrassIA). */
    record Command(UUID actorId, UUID breweryId, SourceType type, String name, String owner, String url,
            String licenseName, PermissionStatus permissionStatus, String attribution, String reviewFrequency,
            String responsible) {}

    record Result(UUID id) {}
}
