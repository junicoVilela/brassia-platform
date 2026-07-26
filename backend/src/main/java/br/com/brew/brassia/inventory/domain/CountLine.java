package br.com.brew.brassia.inventory.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Linha de uma contagem física: o que foi contado num lote e o saldo do sistema
 * capturado no momento da contagem (para conciliação). A quantidade contada é a
 * evidência — permanece registrada mesmo após o ajuste.
 */
public record CountLine(UUID lotId, UUID ingredientId, StockUnit unit, BigDecimal countedQuantity,
        BigDecimal systemQuantity) {

    public CountLine {
        Objects.requireNonNull(lotId, "lotId");
        Objects.requireNonNull(ingredientId, "ingredientId");
        Objects.requireNonNull(unit, "unit");
        if (countedQuantity == null || countedQuantity.signum() < 0) {
            throw new IllegalArgumentException("quantidade contada não pode ser negativa");
        }
        systemQuantity = systemQuantity == null ? BigDecimal.ZERO : systemQuantity;
    }

    /** Diferença registrada na contagem (contado − sistema no momento da contagem). */
    public BigDecimal difference() {
        return countedQuantity.subtract(systemQuantity);
    }
}
