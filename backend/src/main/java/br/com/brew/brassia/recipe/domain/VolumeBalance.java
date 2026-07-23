package br.com.brew.brassia.recipe.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Motor de volumes (REC-002): a partir do volume final desejado, calcula a água
 * necessária mostrando cada parcela — absorção do grão, evaporação e perdas
 * (dead space). O balanço fecha por construção:
 * {@code águaTotal = volumeFinal + absorção + evaporação + perdas}.
 */
public final class VolumeBalance {
    /** Identificador do método/versão do cálculo (persistível por quem consome). */
    public static final String METHOD = "GRAIN_ABSORPTION_BOILOFF_V1";

    private static final BigDecimal ABSORPTION_L_PER_KG = new BigDecimal("1.0");
    private static final BigDecimal MINUTES_PER_HOUR = new BigDecimal("60");
    private static final int SCALE = 2;

    private VolumeBalance() {
    }

    /**
     * @param finalVolumeLiters      volume final desejado (no fermentador)
     * @param grainMassKg            massa total de grãos da mostura
     * @param boilTimeMinutes        tempo de fervura (nulo = 0)
     * @param deadSpaceLiters        perda de dead space do equipamento
     * @param boilOffLitersPerHour   taxa de evaporação do equipamento
     */
    public static Result compute(BigDecimal finalVolumeLiters, BigDecimal grainMassKg, Integer boilTimeMinutes,
            BigDecimal deadSpaceLiters, BigDecimal boilOffLitersPerHour) {
        var finalVolume = requireNonNegative(finalVolumeLiters, "volume final");
        var grain = requireNonNegative(grainMassKg, "massa de grãos");
        var deadSpace = requireNonNegative(deadSpaceLiters, "dead space");
        var boilOffRate = requireNonNegative(boilOffLitersPerHour, "evaporação");
        var boilHours = BigDecimal.valueOf(boilTimeMinutes == null ? 0 : boilTimeMinutes)
                .divide(MINUTES_PER_HOUR, 4, RoundingMode.HALF_UP);

        var absorption = scale(grain.multiply(ABSORPTION_L_PER_KG));
        var evaporation = scale(boilOffRate.multiply(boilHours));
        var losses = scale(deadSpace);
        var preBoilVolume = scale(finalVolume.add(evaporation));
        var totalWater = scale(finalVolume.add(absorption).add(evaporation).add(losses));

        return new Result(scale(finalVolume), absorption, evaporation, losses, preBoilVolume, totalWater, METHOD);
    }

    private static BigDecimal requireNonNegative(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(field + " não pode ser negativo");
        }
        return value;
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Cada parcela do balanço e o total. {@code grainAbsorptionLiters} é a água
     * retida no grão; {@code evaporationLiters} a perda por fervura; {@code
     * lossesLiters} o dead space; {@code totalWaterLiters} a soma de todas.
     */
    public record Result(
            BigDecimal finalVolumeLiters,
            BigDecimal grainAbsorptionLiters,
            BigDecimal evaporationLiters,
            BigDecimal lossesLiters,
            BigDecimal preBoilVolumeLiters,
            BigDecimal totalWaterLiters,
            String method) {}
}
