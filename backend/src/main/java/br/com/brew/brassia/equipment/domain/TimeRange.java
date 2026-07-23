package br.com.brew.brassia.equipment.domain;

import java.time.Instant;
import java.util.Objects;

/** Intervalo [start, end) com fim estritamente após o início. */
public record TimeRange(Instant startAt, Instant endAt) {
    public TimeRange {
        Objects.requireNonNull(startAt, "startAt");
        Objects.requireNonNull(endAt, "endAt");
        if (!endAt.isAfter(startAt)) {
            throw new IllegalArgumentException("fim deve ser posterior ao início");
        }
    }

    /** Sobreposição de meio-aberto: verdadeiro se os intervalos compartilham algum instante. */
    public boolean overlaps(TimeRange other) {
        return startAt.isBefore(other.endAt) && other.startAt.isBefore(endAt);
    }
}
