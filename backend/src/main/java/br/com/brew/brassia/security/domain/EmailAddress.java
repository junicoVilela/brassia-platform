package br.com.brew.brassia.security.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * E-mail interno. O valor exibível preserva o original informado; o valor
 * {@link #normalized()} (trim + minúsculas) é a autoridade de unicidade.
 */
public record EmailAddress(String value) {
    // Validação pragmática: um "@", partes não vazias e um ponto no domínio.
    private static final Pattern FORMAT = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int MAX_LENGTH = 254;

    public EmailAddress {
        value = value == null ? "" : value.trim();
        if (value.isBlank() || value.length() > MAX_LENGTH || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("email inválido");
        }
    }

    public String normalized() {
        return value.toLowerCase(Locale.ROOT);
    }
}
