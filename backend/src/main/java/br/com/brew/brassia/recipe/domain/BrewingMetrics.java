package br.com.brew.brassia.recipe.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Motor de metas cervejeiras (REC-003): OG, FG, ABV, IBU, cor e atenuação.
 * Método versionado {@code TINSETH_MOREY} v1:
 * <ul>
 *   <li>Gravidade por extrato: pontos = massa(kg) × (potencial−1) × 1000, × eficiência ÷ volume.</li>
 *   <li>FG/ABV a partir da atenuação da levedura; ABV = (OG−FG) × 131,25.</li>
 *   <li>IBU por Tinseth (métrico); cor por Morey (via conversão SRM/EBC).</li>
 * </ul>
 * Cálculo puro; usa {@code double} internamente e arredonda na saída.
 */
public final class BrewingMetrics {
    public static final String METHOD = "TINSETH_MOREY";
    public static final int VERSION = 1;

    private static final double DEFAULT_ATTENUATION_PERCENT = 75.0;
    private static final double ABV_FACTOR = 131.25;
    private static final double LB_PER_KG = 2.20462;
    private static final double GAL_PER_LITER = 0.264172;
    private static final double EBC_PER_SRM = 1.97;

    private BrewingMetrics() {
    }

    public static Result compute(BigDecimal batchVolumeLiters, BigDecimal mashEfficiency,
            List<Fermentable> fermentables, List<Hop> hops, BigDecimal yeastAttenuationPercent) {
        double volume = batchVolumeLiters.doubleValue();
        if (volume <= 0) {
            throw new IllegalArgumentException("volume deve ser positivo");
        }
        double efficiency = mashEfficiency.doubleValue();

        double gravityPoints = 0;
        double mcu = 0;
        double volGallons = volume * GAL_PER_LITER;
        for (var f : fermentables) {
            double massKg = f.massKg().doubleValue();
            if (f.potentialSg() != null) {
                gravityPoints += massKg * (f.potentialSg().doubleValue() - 1.0) * 1000.0;
            }
            if (f.colorEbc() != null && volGallons > 0) {
                double srmColor = f.colorEbc().doubleValue() / EBC_PER_SRM;
                mcu += (srmColor * massKg * LB_PER_KG) / volGallons;
            }
        }

        double ogPoints = gravityPoints * efficiency / volume;
        double og = 1.0 + ogPoints / 1000.0;
        double attenuation = (yeastAttenuationPercent == null ? DEFAULT_ATTENUATION_PERCENT
                : yeastAttenuationPercent.doubleValue()) / 100.0;
        double fgPoints = ogPoints * (1.0 - attenuation);
        double fg = 1.0 + fgPoints / 1000.0;
        double abv = (og - fg) * ABV_FACTOR;

        double bigness = 1.65 * Math.pow(0.000125, og - 1.0);
        double ibu = 0;
        for (var h : hops) {
            if (h.alphaAcidPercent() == null) {
                continue;
            }
            double timeFactor = (1.0 - Math.exp(-0.04 * h.timeMinutes())) / 4.15;
            double utilization = bigness * timeFactor;
            double mgPerLiterAlpha = (h.alphaAcidPercent().doubleValue() / 100.0 * h.massGrams().doubleValue()
                    * 1000.0) / volume;
            ibu += utilization * mgPerLiterAlpha;
        }

        double srm = mcu > 0 ? 1.4922 * Math.pow(mcu, 0.6859) : 0;
        double colorEbc = srm * EBC_PER_SRM;

        return new Result(
                round(ogPoints, 1), round(og, 4), round(fgPoints, 1), round(fg, 4), round(abv, 2),
                round(ibu, 1), round(colorEbc, 1), round(attenuation * 100.0, 1), METHOD, VERSION);
    }

    private static BigDecimal round(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }

    /** Fermentável: massa em kg + potencial de extrato + cor (EBC). */
    public record Fermentable(BigDecimal massKg, BigDecimal potentialSg, BigDecimal colorEbc) {}

    /** Lúpulo: massa em gramas + alfa-ácido (%) + tempo de fervura (min). */
    public record Hop(BigDecimal massGrams, BigDecimal alphaAcidPercent, int timeMinutes) {}

    public record Result(BigDecimal ogPoints, BigDecimal ogSg, BigDecimal fgPoints, BigDecimal fgSg,
            BigDecimal abv, BigDecimal ibu, BigDecimal colorEbc, BigDecimal attenuationPercent,
            String method, int version) {}
}
