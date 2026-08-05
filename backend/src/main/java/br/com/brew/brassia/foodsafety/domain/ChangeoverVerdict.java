package br.com.brew.brassia.foodsafety.domain;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Resultado da avaliação de troca de produto (FDS-001).
 *
 * <p>O veredito carrega o motivo <em>e</em> os alergênicos em questão, porque um bloqueio que diz
 * apenas "não pode" obriga o operador a adivinhar qual POP resolve. O {@code code} é estável e vai
 * ao Problem Details; a frase é segura e pode mudar sem quebrar contrato.
 */
public record ChangeoverVerdict(Outcome outcome, String detail, Set<AllergenCode> allergens,
        List<AllergenProfile.Gap> gaps) {

    public ChangeoverVerdict {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(detail, "detail");
        allergens = allergens == null ? Set.of() : new TreeSet<>(allergens);
        gaps = gaps == null ? List.of() : List.copyOf(gaps);
    }

    public enum Outcome {
        /** Não há carga a remover: o equipamento é novo, ou o produto que entra já aceita o que havia. */
        CLEAR(true),
        /** Equipamento dedicado que comporta o produto — a garantia é a dedicação, não a limpeza. */
        DEDICATED(true),
        /** Havia carga, e o POP liberado remove exatamente o que precisava sair. */
        CLEANED(true),
        /** Falta declaração de alergênico em algum ingrediente: não dá para afirmar nada. */
        UNDECLARED(false),
        /** O produto traz alergênico que a dedicação do equipamento não admite. */
        DEDICATION_VIOLATED(false),
        /** Carga residual sem POP compatível liberado depois do último uso. */
        CHANGEOVER_REQUIRED(false);

        private final boolean allowed;

        Outcome(boolean allowed) {
            this.allowed = allowed;
        }

        public boolean allowed() {
            return allowed;
        }
    }

    public boolean allowed() {
        return outcome.allowed();
    }

    /** Código estável do bloqueio, para o Problem Details e para a lista de impedimentos do envase. */
    public String code() {
        return switch (outcome) {
            case UNDECLARED -> "allergen_undeclared";
            case DEDICATION_VIOLATED -> "allergen_dedication_violated";
            case CHANGEOVER_REQUIRED -> "allergen_changeover_required";
            case CLEAR, DEDICATED, CLEANED -> "allergen_clear";
        };
    }

    static ChangeoverVerdict allowed(Outcome outcome, String detail, Set<AllergenCode> allergens) {
        return new ChangeoverVerdict(outcome, detail, allergens, List.of());
    }

    static ChangeoverVerdict undeclared(String detail, List<AllergenProfile.Gap> gaps) {
        return new ChangeoverVerdict(Outcome.UNDECLARED, detail, Set.of(), gaps);
    }

    static ChangeoverVerdict blocked(Outcome outcome, String detail, Set<AllergenCode> allergens) {
        return new ChangeoverVerdict(outcome, detail, allergens, List.of());
    }
}
