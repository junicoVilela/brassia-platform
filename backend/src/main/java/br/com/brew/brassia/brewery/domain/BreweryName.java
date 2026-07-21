package br.com.brew.brassia.brewery.domain;

public record BreweryName(String value) {
    private static final int MAX_LENGTH = 160;

    public BreweryName {
        value = value == null ? "" : value.trim();
        if (value.isBlank() || value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("nome deve conter de 1 a 160 caracteres");
        }
    }
}
