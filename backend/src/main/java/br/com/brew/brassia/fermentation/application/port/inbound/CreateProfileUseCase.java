package br.com.brew.brassia.fermentation.application.port.inbound;

import java.util.List;
import java.util.UUID;

/** Cria um perfil de fermentação (FER-001) em rascunho; nova versão se o código já foi publicado. */
public interface CreateProfileUseCase {
    Result handle(Command command);

    record Command(UUID actorId, UUID breweryId, String code, String name, List<StageInput> stages) {}

    record Result(UUID id, int version) {}
}
