package br.com.brew.brassia.quality.domain;

/** Validação de texto compartilhada pelos registros do tratamento (QLT-002). */
final class Texts {

    private Texts() {
    }

    static String require(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " é obrigatória");
        }
        var trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new IllegalArgumentException(field + " excede " + max + " caracteres");
        }
        return trimmed;
    }
}
