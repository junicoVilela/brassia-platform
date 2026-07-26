package br.com.brew.brassia.planning.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Janela de ocupação do equipamento por uma entrada da agenda: intervalo
 * semiaberto {@code [start, end)}. Invariante: {@code end} estritamente após
 * {@code start}. Base para a detecção de conflito de equipamento (PLN-001).
 */
public record ScheduleWindow(Instant start, Instant end) {

    public ScheduleWindow {
        Objects.requireNonNull(start, "start is required");
        Objects.requireNonNull(end, "end is required");
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("fim da janela deve ser posterior ao início");
        }
    }

    /**
     * Duas janelas se sobrepõem quando compartilham qualquer instante do intervalo
     * semiaberto. Janelas que apenas se tocam (o fim de uma = o início da outra)
     * não conflitam.
     */
    public boolean overlaps(ScheduleWindow other) {
        Objects.requireNonNull(other, "other is required");
        return start.isBefore(other.end) && other.start.isBefore(end);
    }
}
