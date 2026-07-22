package br.com.brew.brassia.catalog.domain;

import java.util.Locale;
import java.util.Set;

/**
 * Tipo de ingrediente e os atributos específicos que ele admite (CAT-001). O
 * conjunto de atributos permitidos é a autoridade de validação: chave fora do
 * conjunto é rejeitada. Os atributos aqui são um mínimo representativo por tipo;
 * novos atributos entram estendendo o conjunto.
 */
public enum IngredientType {
    MALT(Set.of("colorEbc", "potentialSg")),
    HOP(Set.of("alphaAcid", "form")),
    YEAST(Set.of("attenuation", "tempMinC", "tempMaxC")),
    SALT(Set.of("ion")),
    ADJUNCT(Set.of()),
    PACKAGING(Set.of("volumeMl", "material"));

    private final Set<String> allowedAttributes;

    IngredientType(Set<String> allowedAttributes) {
        this.allowedAttributes = allowedAttributes;
    }

    public Set<String> allowedAttributes() {
        return allowedAttributes;
    }

    public static IngredientType of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("tipo obrigatório");
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("tipo de ingrediente inválido");
        }
    }
}
