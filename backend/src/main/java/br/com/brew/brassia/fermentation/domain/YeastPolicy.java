package br.com.brew.brassia.fermentation.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Política de reutilização de levedura da cervejaria (YST-002): até que geração, com que
 * idade e a partir de que viabilidade uma coleta ainda é recomendável.
 *
 * <p>Vive na cervejaria, e não no perfil de fermentação, porque a decisão de repitch é da
 * casa e não do estilo — e assim a recomendação não depende do vínculo lote↔perfil, que só
 * chega na FER-004.
 */
public record YeastPolicy(int maxGeneration, int maxAgeDays, BigDecimal minViabilityPercent) {

    /** Padrão conservador, aplicado à cervejaria que ainda não configurou a sua política. */
    public static YeastPolicy defaults() {
        return new YeastPolicy(10, 21, new BigDecimal("70"));
    }

    public YeastPolicy {
        if (maxGeneration < 1) {
            throw new IllegalArgumentException("geração máxima deve ser positiva");
        }
        if (maxAgeDays < 1) {
            throw new IllegalArgumentException("idade máxima deve ser positiva (dias)");
        }
        Objects.requireNonNull(minViabilityPercent, "viabilidade mínima é obrigatória");
        if (minViabilityPercent.signum() < 0 || minViabilityPercent.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("viabilidade mínima deve estar entre 0 e 100%");
        }
    }
}
