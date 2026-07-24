package br.com.brew.brassia.referencedata.domain;

import java.time.Instant;
import java.util.Objects;

/** Proveniência de um dataset: sistema/origem, identificador, URL e data de obtenção. */
public record Provenance(String sourceSystem, String sourceRecordId, String sourceUrl, Instant retrievedAt) {
    public Provenance {
        sourceSystem = sourceSystem == null ? "" : sourceSystem.trim();
        if (sourceSystem.isBlank()) {
            throw new IllegalArgumentException("sourceSystem é obrigatório");
        }
        Objects.requireNonNull(retrievedAt, "retrievedAt é obrigatório");
        sourceRecordId = blankToNull(sourceRecordId);
        sourceUrl = blankToNull(sourceUrl);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
