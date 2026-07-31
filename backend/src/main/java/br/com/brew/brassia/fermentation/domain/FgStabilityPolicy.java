package br.com.brew.brassia.fermentation.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Critério de estabilidade de FG (FER-003), configurado no perfil de fermentação: a série de
 * densidade precisa cobrir {@code windowHours}, com ao menos {@code minReadings} leituras, e
 * variar no máximo {@code toleranceSg} entre a menor e a maior. Como vive no perfil, a versão
 * publicada congela o critério — uma avaliação passada continua reproduzível.
 */
public record FgStabilityPolicy(int windowHours, int minReadings, BigDecimal toleranceSg) {

    /** Padrão aplicado a perfis que não declaram o critério (inclusive os criados antes da FER-003). */
    public static FgStabilityPolicy defaults() {
        return new FgStabilityPolicy(48, 3, new BigDecimal("0.0020"));
    }

    public FgStabilityPolicy {
        if (windowHours <= 0) {
            throw new IllegalArgumentException("janela de estabilidade deve ser positiva (horas)");
        }
        if (minReadings < 2) {
            throw new IllegalArgumentException("estabilidade exige ao menos 2 leituras");
        }
        Objects.requireNonNull(toleranceSg, "tolerância é obrigatória");
        if (toleranceSg.signum() <= 0) {
            throw new IllegalArgumentException("tolerância deve ser positiva (SG)");
        }
    }
}
