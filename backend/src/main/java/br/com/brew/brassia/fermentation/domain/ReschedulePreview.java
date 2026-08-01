package br.com.brew.brassia.fermentation.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Efeito calculado de mover uma data na agenda (FER-004), antes de qualquer gravação: o que
 * muda, de quando para quando, e o que ficou de fora e por quê.
 */
public record ReschedulePreview(Duration delta, List<Change> changes, List<Blocked> blocked) {

    /** Etapa que será deslocada, com a janela atual e a proposta. */
    public record Change(UUID stepId, int sequence, String name, Instant fromStart, Instant toStart,
            Instant fromEnd, Instant toEnd) {

        static Change of(ScheduleStep step, Duration delta) {
            return new Change(step.id(), step.sequence(), step.name(), step.plannedStart(),
                    step.plannedStart().plus(delta), step.plannedEnd(), step.plannedEnd().plus(delta));
        }
    }

    /** Etapa que a propagação não move, com o motivo — a cadeia para aqui. */
    public record Blocked(UUID stepId, int sequence, String name, String reason) {

        Blocked(ScheduleStep step, String reason) {
            this(step.id(), step.sequence(), step.name(), reason);
        }
    }

    public ReschedulePreview {
        changes = List.copyOf(changes);
        blocked = List.copyOf(blocked);
    }

    public long deltaHours() {
        return delta.toHours();
    }
}
