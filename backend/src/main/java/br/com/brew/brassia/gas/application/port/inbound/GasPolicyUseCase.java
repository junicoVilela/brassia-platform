package br.com.brew.brassia.gas.application.port.inbound;

import br.com.brew.brassia.gas.domain.GasPolicy;
import java.util.UUID;

/** Leitura e ajuste da política de gases (PRM-001). */
public interface GasPolicyUseCase {

    GasPolicy get(UUID breweryId);

    /** @param months {@code null} volta a exigir o vencimento informado a cada requalificação */
    GasPolicy update(UUID actorId, UUID breweryId, Integer months);
}
