package br.com.brew.brassia.foodsafety.domain;

import java.util.Objects;

/**
 * Alergênico declarado que não existe no vocabulário da cervejaria (FDS-001).
 *
 * <p>Recusar é o ponto: aceitar um código livre deixaria "GLUTEN" e "GLUTEM" conviverem na matriz,
 * e um recall por alergênico procuraria por um deles.
 */
public final class UnknownAllergenException extends RuntimeException {

    private final transient AllergenCode code;

    public UnknownAllergenException(AllergenCode code) {
        super("alergênico não cadastrado: " + code);
        this.code = Objects.requireNonNull(code);
    }

    public AllergenCode code() {
        return code;
    }
}
