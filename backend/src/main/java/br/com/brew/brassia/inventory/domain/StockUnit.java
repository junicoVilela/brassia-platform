package br.com.brew.brassia.inventory.domain;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Unidade do lote de estoque, com conversão para uma unidade canônica por
 * dimensão (massa → KG, volume → L, contagem → UNIT). A conversão permite alocar
 * uma reserva pedida em uma unidade sobre lotes em outra (mesma dimensão).
 */
public enum StockUnit {
    KG("KG", "1"),
    G("KG", "0.001"),
    MG("KG", "0.000001"),
    L("L", "1"),
    ML("L", "0.001"),
    UNIT("UNIT", "1");

    private final String canonical;
    private final BigDecimal toCanonicalFactor;

    StockUnit(String canonical, String factor) {
        this.canonical = canonical;
        this.toCanonicalFactor = new BigDecimal(factor);
    }

    public static StockUnit of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("unidade é obrigatória");
        }
        try {
            return StockUnit.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("unidade inválida: " + value);
        }
    }

    public String canonical() {
        return canonical;
    }

    public boolean sameDimension(StockUnit other) {
        return other != null && canonical.equals(other.canonical);
    }

    public BigDecimal toCanonical(BigDecimal quantity) {
        return quantity.multiply(toCanonicalFactor);
    }

    public BigDecimal fromCanonical(BigDecimal canonicalQuantity) {
        return canonicalQuantity.divide(toCanonicalFactor, 6, java.math.RoundingMode.HALF_UP);
    }
}
