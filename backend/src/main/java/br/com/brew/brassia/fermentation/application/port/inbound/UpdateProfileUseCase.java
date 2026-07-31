package br.com.brew.brassia.fermentation.application.port.inbound;

import java.util.List;
import java.util.UUID;

/** Atualiza um perfil em rascunho (FER-001); publicado é imutável. */
public interface UpdateProfileUseCase {
    void handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID profileId, String name, List<StageInput> stages,
            StabilityInput stability) {}
}
