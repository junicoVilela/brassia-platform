package br.com.brew.brassia.production.adapter.inbound.web.dto;

import br.com.brew.brassia.production.ProductionStockGateway;
import br.com.brew.brassia.production.application.port.inbound.BrewConsumptionUseCases;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Contratos do consumo do dia de brassa (TRC-001-C). */
public final class BrewConsumptionDtos {

    private BrewConsumptionDtos() {
    }

    public record RegisterRequest(@NotEmpty List<@NotNull LineRequest> lines) {}

    public record LineRequest(@NotNull UUID lotId, @NotNull @DecimalMin("0.0001") BigDecimal quantity,
            @NotBlank String unit) {}

    /**
     * @param alreadyRegistered quando verdadeiro, o consumo já foi lançado: a proposta serve só
     *                          para conferência, e registrar de novo dobraria o consumo
     */
    public record ProposalView(UUID orderId, boolean alreadyRegistered, List<ReservedLotView> reserved) {

        public static ProposalView from(BrewConsumptionUseCases.Proposal.Result result) {
            return new ProposalView(result.orderId(), result.alreadyRegistered(),
                    result.reserved().stream().map(ReservedLotView::from).toList());
        }
    }

    public record ReservedLotView(UUID lotId, UUID ingredientId, String ingredientName,
            String supplierLotCode, BigDecimal reserved, String unit) {

        public static ReservedLotView from(ProductionStockGateway.ReservedLot lot) {
            return new ReservedLotView(lot.lotId(), lot.ingredientId(), lot.ingredientName(),
                    lot.supplierLotCode(), lot.reserved(), lot.unit());
        }
    }
}
