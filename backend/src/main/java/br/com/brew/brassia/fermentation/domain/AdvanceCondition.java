package br.com.brew.brassia.fermentation.domain;

import java.util.Arrays;
import java.util.Locale;

/**
 * Critério de avanço de um estágio (FER-001): por tempo, por densidade-alvo (FG) ou
 * manual. O avanço em si (na execução) sempre exige confirmação humana quando o estágio
 * assim o marcar; aqui apenas se declara a condição.
 */
public enum AdvanceCondition {
    TIME,
    GRAVITY,
    MANUAL;

    public static AdvanceCondition of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("critério de avanço é obrigatório");
        }
        var normalized = value.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(c -> c.name().equals(normalized)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("critério de avanço inválido: " + value));
    }
}
