package br.com.brew.brassia.brewery.domain;

import java.time.DateTimeException;
import java.time.ZoneId;

/** Fuso horário da cervejaria; deve ser um {@link ZoneId} válido (ex.: America/Sao_Paulo). */
public record Timezone(String value) {
    public Timezone {
        value = value == null ? "" : value.trim();
        try {
            ZoneId.of(value);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("fuso horário inválido");
        }
    }
}
