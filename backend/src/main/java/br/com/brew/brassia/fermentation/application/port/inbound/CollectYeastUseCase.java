package br.com.brew.brassia.fermentation.application.port.inbound;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Registra uma coleta de levedura (YST-001); nasce em quarentena. */
public interface CollectYeastUseCase {
    Result handle(Command command);

    record Command(UUID actorId, UUID breweryId, String code, UUID strainId, UUID sourceBatchId,
            UUID parentHarvestId, Instant harvestedAt, BigDecimal viabilityPercent, String condition,
            String storageLocation, BigDecimal storageTempC) {}

    record Result(UUID id, int generation) {}
}
