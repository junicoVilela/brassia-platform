package br.com.brew.brassia.sanitation.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Consumo medido de um ciclo (CLN-005): água (L), energia (kWh) e produto/químico (kg).
 * É apenas medição — a comparação/otimização é consultiva e não reduz parâmetros do POP
 * (redução exige nova versão publicada).
 */
public record Consumption(BigDecimal waterLiters, BigDecimal energyKwh, BigDecimal productKg, Instant recordedAt) {

    public Consumption {
        requireNonNegative(waterLiters, "água");
        requireNonNegative(energyKwh, "energia");
        requireNonNegative(productKg, "produto");
        Objects.requireNonNull(recordedAt, "recordedAt");
    }

    public static Consumption of(BigDecimal waterLiters, BigDecimal energyKwh, BigDecimal productKg) {
        return new Consumption(waterLiters, energyKwh, productKg, Instant.now());
    }

    private static void requireNonNegative(BigDecimal value, String field) {
        Objects.requireNonNull(value, field);
        if (value.signum() < 0) {
            throw new IllegalArgumentException(field + " não pode ser negativo");
        }
    }
}
