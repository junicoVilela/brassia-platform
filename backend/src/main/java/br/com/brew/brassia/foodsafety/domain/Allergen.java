package br.com.brew.brassia.foodsafety.domain;

import java.util.Objects;
import java.util.UUID;

/** Alergênico do vocabulário da cervejaria (FDS-001): o código estável e o nome que vai ao rótulo. */
public record Allergen(UUID id, UUID breweryId, AllergenCode code, String name) {

    private static final int MAX_NAME = 120;

    public Allergen {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(breweryId, "breweryId");
        Objects.requireNonNull(code, "código do alergênico é obrigatório");
        Objects.requireNonNull(name, "nome do alergênico é obrigatório");
        name = name.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("nome do alergênico é obrigatório");
        }
        if (name.length() > MAX_NAME) {
            throw new IllegalArgumentException("nome do alergênico excede " + MAX_NAME + " caracteres");
        }
    }

    public static Allergen register(UUID breweryId, String code, String name) {
        return new Allergen(UUID.randomUUID(), breweryId, AllergenCode.of(code), name);
    }
}
