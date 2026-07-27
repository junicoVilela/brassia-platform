package br.com.brew.brassia.production.adapter.inbound.web.dto;

import br.com.brew.brassia.production.domain.BatchTransfer;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferView(
        UUID id, UUID destinationEquipmentId, BigDecimal volumeLiters, BigDecimal ogSg, BigDecimal lossesLiters,
        Instant transferredAt, UUID transferredBy) {

    public static TransferView from(BatchTransfer t) {
        return new TransferView(t.id(), t.destinationEquipmentId(), t.volumeLiters(), t.ogSg(), t.lossesLiters(),
                t.transferredAt(), t.transferredBy());
    }
}
