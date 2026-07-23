package br.com.brew.brassia.recipe.domain;

import java.util.Locale;

/** Vocabulário fechado de unidades de item de receita. */
public enum RecipeUnit {
    KG,
    G,
    MG,
    L,
    ML,
    UNIT;

    public static RecipeUnit of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("unidade obrigatória");
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unidade inválida");
        }
    }
}
