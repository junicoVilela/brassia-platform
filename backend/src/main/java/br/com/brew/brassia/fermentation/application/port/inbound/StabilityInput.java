package br.com.brew.brassia.fermentation.application.port.inbound;

import br.com.brew.brassia.fermentation.domain.FgStabilityPolicy;
import java.math.BigDecimal;

/**
 * Critério de estabilidade de FG informado no perfil (FER-003). Opcional: perfil que não o
 * declara fica com o padrão do domínio, para não obrigar a cervejaria a arbitrar um número
 * antes de ter dado histórico.
 */
public record StabilityInput(Integer windowHours, Integer minReadings, BigDecimal toleranceSg) {

    public static FgStabilityPolicy toPolicy(StabilityInput input) {
        if (input == null) {
            return FgStabilityPolicy.defaults();
        }
        var defaults = FgStabilityPolicy.defaults();
        return new FgStabilityPolicy(
                input.windowHours() == null ? defaults.windowHours() : input.windowHours(),
                input.minReadings() == null ? defaults.minReadings() : input.minReadings(),
                input.toleranceSg() == null ? defaults.toleranceSg() : input.toleranceSg());
    }
}
