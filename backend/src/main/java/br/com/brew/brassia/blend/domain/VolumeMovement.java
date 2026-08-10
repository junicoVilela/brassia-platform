package br.com.brew.brassia.blend.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Volume saindo de um lote ou entrando nele (BLD-001).
 *
 * <p>O volume é sempre positivo e o sentido vem de onde o movimento está — entrada ou saída. Guardar o
 * sentido no sinal do número transformaria todo erro de sinal num balanço que fecha por acidente.
 */
public record VolumeMovement(UUID batchId, BigDecimal liters) {

    public VolumeMovement {
        Objects.requireNonNull(batchId, "batchId");
        Objects.requireNonNull(liters, "liters");
        if (liters.signum() <= 0) {
            throw new IllegalArgumentException("volume precisa ser positivo: " + liters);
        }
    }
}
