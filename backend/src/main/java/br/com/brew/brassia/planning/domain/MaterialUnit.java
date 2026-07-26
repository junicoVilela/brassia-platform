package br.com.brew.brassia.planning.domain;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Conversão de unidades de material para uma unidade canônica por dimensão
 * (massa → KG, volume → L, contagem → UNIT). Usada na necessidade de materiais
 * (PLN-002) para somar quantidades de um mesmo ingrediente em unidades diferentes.
 */
public final class MaterialUnit {

    /** Fator multiplicativo até a unidade canônica da dimensão. */
    private record Conversion(String canonical, BigDecimal toCanonical) {}

    private static final Map<String, Conversion> CONVERSIONS = Map.of(
            "KG", new Conversion("KG", new BigDecimal("1")),
            "G", new Conversion("KG", new BigDecimal("0.001")),
            "MG", new Conversion("KG", new BigDecimal("0.000001")),
            "L", new Conversion("L", new BigDecimal("1")),
            "ML", new Conversion("L", new BigDecimal("0.001")),
            "UNIT", new Conversion("UNIT", new BigDecimal("1")));

    private MaterialUnit() {
    }

    /** Símbolo da unidade canônica para a unidade informada. */
    public static String canonicalOf(String unit) {
        return conversion(unit).canonical();
    }

    /** Converte {@code quantity} da {@code unit} para a quantidade na unidade canônica. */
    public static BigDecimal toCanonical(BigDecimal quantity, String unit) {
        return quantity.multiply(conversion(unit).toCanonical());
    }

    private static Conversion conversion(String unit) {
        var conversion = unit == null ? null : CONVERSIONS.get(unit.toUpperCase(java.util.Locale.ROOT));
        if (conversion == null) {
            throw new IllegalArgumentException("unidade desconhecida: " + unit);
        }
        return conversion;
    }
}
