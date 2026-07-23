package br.com.brew.brassia.recipe.domain;

import java.util.Locale;

/** Etapa do processo em que um item entra na receita. */
public enum RecipeStage {
    MASH,
    BOIL,
    WHIRLPOOL,
    FERMENTATION,
    PACKAGING;

    public static RecipeStage of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("etapa obrigatória");
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("etapa inválida");
        }
    }
}
