package br.com.brew.brassia.purchasing.adapter.inbound.web.dto;

import br.com.brew.brassia.purchasing.application.port.inbound.PurchaseNeedUseCase;
import java.math.BigDecimal;
import java.util.UUID;

public record PurchaseNeedView(
        UUID ingredientId, BigDecimal demand, BigDecimal onHand, BigDecimal suggested, String unit) {

    public static PurchaseNeedView from(PurchaseNeedUseCase.Need need) {
        return new PurchaseNeedView(
                need.ingredientId(), need.demand(), need.onHand(), need.suggested(), need.unit());
    }
}
