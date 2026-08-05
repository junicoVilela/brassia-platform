package br.com.brew.brassia.foodsafety.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * Código de um alergênico no vocabulário da cervejaria (FDS-001).
 *
 * <p><strong>Não existe lista fixa aqui, e é a decisão central da história.</strong> Embutir a
 * relação regulatória no código seria congelar uma norma que muda, difere por país e não é da
 * plataforma — e a casa que assina o rótulo é quem responde por ela. O que o domínio garante é a
 * forma: código estável, comparável e sem ambiguidade de caixa, para que "GLUTEN" e "gluten" nunca
 * sejam dois alergênicos diferentes no meio de um recall.
 */
public record AllergenCode(String value) implements Comparable<AllergenCode> {

    private static final int MAX = 40;

    public AllergenCode {
        Objects.requireNonNull(value, "código do alergênico é obrigatório");
        value = value.trim().toUpperCase(Locale.ROOT);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("código do alergênico é obrigatório");
        }
        if (value.length() > MAX) {
            throw new IllegalArgumentException("código do alergênico excede " + MAX + " caracteres");
        }
        if (!value.matches("[A-Z0-9][A-Z0-9_-]*")) {
            throw new IllegalArgumentException(
                    "código do alergênico aceita letras, dígitos, hífen e sublinhado: " + value);
        }
    }

    public static AllergenCode of(String value) {
        return new AllergenCode(value);
    }

    @Override
    public int compareTo(AllergenCode other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
