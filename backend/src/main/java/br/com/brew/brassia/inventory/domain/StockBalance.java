package br.com.brew.brassia.inventory.domain;

import java.math.BigDecimal;

/** Saldo derivado de um lote: físico (on-hand), reservado e disponível. */
public record StockBalance(BigDecimal onHand, BigDecimal reserved) {

    public StockBalance {
        onHand = onHand == null ? BigDecimal.ZERO : onHand;
        reserved = reserved == null ? BigDecimal.ZERO : reserved;
    }

    public BigDecimal available() {
        return onHand.subtract(reserved);
    }
}
