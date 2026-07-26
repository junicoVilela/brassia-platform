package br.com.brew.brassia.purchasing.application.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;

/**
 * Converte uma quantidade na unidade técnica canônica (KG/L/UNIT) para a unidade
 * de compra do ingrediente, quando pertencem à mesma dimensão. Sem tamanho de
 * embalagem modelado (PUR-002-A), PACK e unidades de outra dimensão caem no
 * fallback: mantém a quantidade e a unidade canônica.
 */
final class PurchaseUnitConversion {

    private record Conversion(String canonical, BigDecimal toCanonical) {}

    private static final Map<String, Conversion> UNITS = Map.of(
            "KG", new Conversion("KG", new BigDecimal("1")),
            "G", new Conversion("KG", new BigDecimal("0.001")),
            "MG", new Conversion("KG", new BigDecimal("0.000001")),
            "L", new Conversion("L", new BigDecimal("1")),
            "ML", new Conversion("L", new BigDecimal("0.001")),
            "UNIT", new Conversion("UNIT", new BigDecimal("1")));

    /** Quantidade convertida + rótulo da unidade efetivamente aplicada. */
    record Result(BigDecimal quantity, String unit) {}

    private PurchaseUnitConversion() {
    }

    static Result convert(BigDecimal canonicalQuantity, String canonicalUnit, String purchaseUnit) {
        var target = purchaseUnit == null ? null : UNITS.get(purchaseUnit.toUpperCase(Locale.ROOT));
        if (target == null || !target.canonical().equals(canonicalUnit)) {
            return new Result(canonicalQuantity, canonicalUnit); // fallback (inclui PACK)
        }
        var quantity = canonicalQuantity.divide(target.toCanonical(), 4, RoundingMode.HALF_UP);
        return new Result(quantity, purchaseUnit.toUpperCase(Locale.ROOT));
    }
}
