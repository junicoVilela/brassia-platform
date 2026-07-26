package br.com.brew.brassia.purchasing.application.port.inbound;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Sugestão de necessidade de compra por ingrediente (PUR-001) — leitura, não cria pedido. */
public interface PurchaseNeedUseCase {
    List<Need> handle(UUID breweryId);

    record Need(UUID ingredientId, BigDecimal demand, BigDecimal onHand, BigDecimal reserved,
            BigDecimal suggested, String unit) {}
}
