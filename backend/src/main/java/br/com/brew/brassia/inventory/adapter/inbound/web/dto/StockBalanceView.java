package br.com.brew.brassia.inventory.adapter.inbound.web.dto;

import br.com.brew.brassia.inventory.domain.StockBalance;
import java.math.BigDecimal;

public record StockBalanceView(BigDecimal onHand, BigDecimal reserved, BigDecimal available) {
    public static StockBalanceView from(StockBalance b) {
        return new StockBalanceView(b.onHand(), b.reserved(), b.available());
    }
}
