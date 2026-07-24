package br.com.brew.brassia.water.application.port.inbound;

import br.com.brew.brassia.water.domain.IonProfile;
import java.math.BigDecimal;
import java.util.UUID;

/** Cria (em rascunho) um perfil de água de referência educativo e versionado. */
public interface CreateWaterReferenceProfileUseCase {

    Result handle(Command command);

    record Command(UUID actorId, UUID breweryId, String name, String region, String edition, IonProfile ions,
            BigDecimal alkalinity, BigDecimal hardness, BigDecimal ph, UUID sourceId, String sourceName) {}

    record Result(UUID id, String status) {}
}
