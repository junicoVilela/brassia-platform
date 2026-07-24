package br.com.brew.brassia.referencedata.domain;

/** Checksum SHA-256 (64 hex minúsculos) do payload bruto imutável do dataset. */
public record Checksum(String value) {
    public Checksum {
        value = value == null ? "" : value.trim().toLowerCase();
        if (!value.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("checksum deve ser SHA-256 em hex de 64 caracteres");
        }
    }
}
