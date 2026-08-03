package br.com.brew.brassia.quality.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** O que se fez de imediato para parar o dano: segregar lote, bloquear linha, reter remessa. */
public record Containment(String description, Instant takenAt, UUID takenBy) {

    public Containment {
        description = Texts.require(description, "descrição da contenção", 1000);
        Objects.requireNonNull(takenAt, "instante da contenção");
        Objects.requireNonNull(takenBy, "responsável pela contenção");
    }
}
