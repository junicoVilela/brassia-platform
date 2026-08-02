package br.com.brew.brassia.packaging.domain;

import java.util.Locale;

/**
 * Método de carbonatação (PKG-002). São caminhos diferentes para o mesmo alvo: priming refermenta
 * açúcar na embalagem; forçada aplica pressão de CO₂ até o equilíbrio.
 */
public enum CarbonationMethod {
    PRIMING,
    FORCED;

    public static CarbonationMethod of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("método de carbonatação é obrigatório");
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("método de carbonatação inválido");
        }
    }
}
