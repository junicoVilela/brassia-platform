package br.com.brew.brassia.fermentation.adapter.inbound.web.dto;

import br.com.brew.brassia.fermentation.domain.YeastHarvest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record YeastHarvestView(
        UUID id, String code, UUID strainId, UUID sourceBatchId, UUID parentHarvestId, int generation,
        Instant harvestedAt, BigDecimal viabilityPercent, String condition, String storageLocation,
        BigDecimal storageTempC, String status, boolean available, String reviewNote, Instant reviewedAt) {

    public static YeastHarvestView from(YeastHarvest h) {
        return new YeastHarvestView(h.id(), h.code(), h.strainId(), h.sourceBatchId(), h.parentHarvestId(),
                h.generation(), h.harvestedAt(), h.viabilityPercent(), h.condition(), h.storageLocation(),
                h.storageTempC(), h.status().name(), h.available(), h.reviewNote(), h.reviewedAt());
    }
}
