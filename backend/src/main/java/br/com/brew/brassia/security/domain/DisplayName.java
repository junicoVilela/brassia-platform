package br.com.brew.brassia.security.domain;

public record DisplayName(String value) {
    private static final int MAX_LENGTH = 160;

    public DisplayName {
        value = value == null ? "" : value.trim();
        if (value.isBlank() || value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("display name deve conter de 1 a 160 caracteres");
        }
    }
}
