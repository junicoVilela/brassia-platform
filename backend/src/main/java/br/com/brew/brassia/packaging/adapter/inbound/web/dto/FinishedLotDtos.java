package br.com.brew.brassia.packaging.adapter.inbound.web.dto;

import br.com.brew.brassia.packaging.domain.FinishedLot;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Contratos do lote de produto acabado (TRC-001-B). */
public final class FinishedLotDtos {

    private FinishedLotDtos() {
    }

    /**
     * @param units apenas as unidades boas — rejeito consumiu embalagem e não virou produto
     */
    public record FinishedLotView(UUID id, String code, UUID runId, UUID planId, UUID batchId,
            String batchCode, UUID containerId, BigDecimal containerVolumeMl, int units,
            BigDecimal volumeLiters, LocalDate packagedOn) {

        public static FinishedLotView from(FinishedLot lot) {
            return new FinishedLotView(lot.id(), lot.code(), lot.runId(), lot.planId(), lot.batchId(),
                    lot.batchCode(), lot.containerId(), lot.containerVolumeMl(), lot.units(),
                    lot.volumeLiters(), lot.packagedOn());
        }
    }
}
