package br.com.brew.brassia.gas.domain;

import java.util.Locale;

/**
 * Componente da rede de gás (GAS-001). Regulador e manifold compartilham identidade, código e
 * limite de pressão, então vivem no mesmo cadastro — o que muda é o papel na conexão.
 */
public enum ComponentKind {
    REGULATOR,
    MANIFOLD;

    public static ComponentKind of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("tipo de componente é obrigatório");
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("tipo de componente inválido");
        }
    }
}
