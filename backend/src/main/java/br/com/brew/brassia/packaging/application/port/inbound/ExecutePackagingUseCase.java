package br.com.brew.brassia.packaging.application.port.inbound;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Registra a execução do envase (PKG-003): unidades boas, rejeitos e o volume que saiu do tanque.
 * A perda é derivada, não digitada — é assim que o balanço fecha.
 */
public interface ExecutePackagingUseCase {
    Result handle(Command command);

    record Command(UUID actorId, UUID breweryId, UUID planId, BigDecimal inputVolumeLiters, int producedUnits,
            int rejectedUnits, String note) {}

    record Result(UUID runId, BigDecimal packagedVolumeLiters, BigDecimal lossesLiters, int containersConsumed) {}
}
