package br.com.brew.brassia.water.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Mistura de fontes de água por balanço de massa (WTR-002). A concentração
 * resultante de cada íon é a média ponderada pelo volume:
 * {@code C = Σ(Cᵢ·Vᵢ) / ΣVᵢ}. Como concentração × volume = massa, o balanço
 * fecha por construção. É um cálculo puro, sem estado.
 */
public final class WaterBlend {
    /** Método de cálculo, informado no resultado. */
    public static final String METHOD = "VOLUME_WEIGHTED_AVERAGE";

    private static final int SCALE = 2;

    private WaterBlend() {
    }

    public static Result simulate(List<Component> components) {
        if (components == null || components.isEmpty()) {
            throw new IllegalArgumentException("informe ao menos uma fonte");
        }
        var totalVolume = BigDecimal.ZERO;
        var ca = BigDecimal.ZERO;
        var mg = BigDecimal.ZERO;
        var na = BigDecimal.ZERO;
        var so4 = BigDecimal.ZERO;
        var cl = BigDecimal.ZERO;
        var hco3 = BigDecimal.ZERO;
        for (var c : components) {
            if (c.volumeLiters() == null || c.volumeLiters().signum() <= 0) {
                throw new IllegalArgumentException("volume de cada fonte deve ser positivo");
            }
            var v = c.volumeLiters();
            var ions = c.ions();
            totalVolume = totalVolume.add(v);
            ca = ca.add(ions.calcium().multiply(v));
            mg = mg.add(ions.magnesium().multiply(v));
            na = na.add(ions.sodium().multiply(v));
            so4 = so4.add(ions.sulfate().multiply(v));
            cl = cl.add(ions.chloride().multiply(v));
            hco3 = hco3.add(ions.bicarbonate().multiply(v));
        }
        var blended = new IonProfile(
                div(ca, totalVolume), div(mg, totalVolume), div(na, totalVolume),
                div(so4, totalVolume), div(cl, totalVolume), div(hco3, totalVolume));
        return new Result(blended, totalVolume);
    }

    private static BigDecimal div(BigDecimal mass, BigDecimal totalVolume) {
        return mass.divide(totalVolume, SCALE, RoundingMode.HALF_UP);
    }

    /** Uma fonte na mistura: sua composição e o volume aportado. */
    public record Component(IonProfile ions, BigDecimal volumeLiters) {}

    public record Result(IonProfile ions, BigDecimal totalVolumeLiters) {}
}
