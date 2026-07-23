package br.com.brew.brassia.water.domain;

public record WaterSourceName(String value) {
    private static final int MAX_LENGTH = 160;

    public WaterSourceName {
        value = value == null ? "" : value.trim();
        if (value.isBlank() || value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("nome deve ter 1 a 160 caracteres");
        }
    }
}
