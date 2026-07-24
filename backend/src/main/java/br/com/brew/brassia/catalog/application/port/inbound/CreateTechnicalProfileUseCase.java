package br.com.brew.brassia.catalog.application.port.inbound;

import br.com.brew.brassia.catalog.domain.PropertyRange;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Cria (em rascunho) o perfil técnico de referência de um ingrediente. */
public interface CreateTechnicalProfileUseCase {

    Result handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID ingredientId, String manufacturer, String origin, String form,
            String purpose, String laboratory, String labCode, Map<String, PropertyRange> ranges,
            List<String> descriptors, UUID sourceId, String sourceName) {}

    record Result(UUID id, String status) {}
}
