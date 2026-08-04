package br.com.brew.brassia.quality.domain;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Política de CAPA da cervejaria (PRM-001): quantos dias cada fase tem, por severidade.
 *
 * <p>É <strong>por severidade</strong> porque o tempo aceitável para conter um problema crítico não
 * é o de um leve. Fecha o débito QLT-002-A, onde os prazos ficaram informados por não haver fonte
 * para derivá-los.
 *
 * <p><strong>Sem política para a severidade, nada muda:</strong> os prazos continuam informados na
 * abertura da não conformidade.
 */
public final class CapaPolicy {

    private final UUID breweryId;
    private final Map<Severity, Deadlines> bySeverity;

    /**
     * Prazos em dias contados da abertura, acumulativos: investigar não pode vencer antes de conter,
     * nem verificar antes de investigar — a mesma ordem que o agregado impõe nas fases.
     */
    public record Deadlines(int containmentDays, int investigationDays, int verificationDays) {

        public Deadlines {
            requirePositive(containmentDays, "contenção");
            requirePositive(investigationDays, "investigação");
            requirePositive(verificationDays, "verificação");
            if (investigationDays < containmentDays || verificationDays < investigationDays) {
                throw new IllegalArgumentException(
                        "os prazos devem seguir a ordem das fases: contenção, investigação e verificação");
            }
        }

        private static void requirePositive(int days, String phase) {
            if (days <= 0) {
                throw new IllegalArgumentException("o prazo de " + phase + " deve ser positivo");
            }
        }
    }

    private CapaPolicy(UUID breweryId, Map<Severity, Deadlines> bySeverity) {
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.bySeverity = new EnumMap<>(Objects.requireNonNull(bySeverity, "prazos"));
    }

    public static CapaPolicy none(UUID breweryId) {
        return new CapaPolicy(breweryId, new EnumMap<>(Severity.class));
    }

    public static CapaPolicy reconstitute(UUID breweryId, Map<Severity, Deadlines> bySeverity) {
        return new CapaPolicy(breweryId, bySeverity);
    }

    public void set(Severity severity, Deadlines deadlines) {
        Objects.requireNonNull(severity, "severidade");
        if (deadlines == null) {
            bySeverity.remove(severity);
            return;
        }
        bySeverity.put(severity, deadlines);
    }

    /** Prazos derivados a partir de {@code openedOn}; vazio quando não há política para a severidade. */
    public Optional<Dates> datesFor(Severity severity, LocalDate openedOn) {
        Objects.requireNonNull(severity, "severidade");
        Objects.requireNonNull(openedOn, "data de abertura");
        return Optional.ofNullable(bySeverity.get(severity))
                .map(d -> new Dates(openedOn.plusDays(d.containmentDays()),
                        openedOn.plusDays(d.investigationDays()),
                        openedOn.plusDays(d.verificationDays())));
    }

    public record Dates(LocalDate containmentDueOn, LocalDate investigationDueOn,
            LocalDate verificationDueOn) {}

    public UUID breweryId() {
        return breweryId;
    }

    public Map<Severity, Deadlines> bySeverity() {
        return Map.copyOf(bySeverity);
    }
}
