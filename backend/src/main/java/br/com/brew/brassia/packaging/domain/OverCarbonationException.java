package br.com.brew.brassia.packaging.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * A cerveja já tem, dissolvido, o CO₂ que se queria atingir. Adicionar açúcar aqui não carbonata
 * mais nada: gera pressão que a embalagem não comporta. O alvo e o residual acompanham o erro para
 * o cervejeiro decidir entre elevar o alvo, resfriar antes ou trocar de método.
 */
public final class OverCarbonationException extends RuntimeException {

    private final BigDecimal targetVolumes;
    private final BigDecimal residualVolumes;

    public OverCarbonationException(BigDecimal targetVolumes, BigDecimal residualVolumes) {
        super("CO₂ residual já atinge o alvo");
        this.targetVolumes = Objects.requireNonNull(targetVolumes);
        this.residualVolumes = Objects.requireNonNull(residualVolumes);
    }

    public BigDecimal targetVolumes() { return targetVolumes; }

    public BigDecimal residualVolumes() { return residualVolumes; }
}
