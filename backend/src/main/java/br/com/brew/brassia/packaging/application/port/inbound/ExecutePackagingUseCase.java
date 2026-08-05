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

    /**
     * @param finishedLotCode código do lote de produto acabado criado pelo envase (TRC-001-B) — é o
     *                        que vai impresso na embalagem e o que um recall recolhe
     */
    record Result(UUID runId, BigDecimal packagedVolumeLiters, BigDecimal lossesLiters,
            int containersConsumed, String finishedLotCode) {}
}
