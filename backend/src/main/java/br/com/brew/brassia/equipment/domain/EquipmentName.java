package br.com.brew.brassia.equipment.domain;

public record EquipmentName(String value) {
    private static final int MAX_LENGTH = 160;

    public EquipmentName {
        value = value == null ? "" : value.trim();
        if (value.isBlank() || value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("nome deve ter 1 a 160 caracteres");
        }
    }
}
