package br.com.brew.brassia.foodsafety.domain;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Perfil de alergênicos de um lote (FDS-001): o que a composição declara, mais o que ela não sabe.
 *
 * <p>As lacunas viajam junto com o resultado, pelo mesmo motivo que a genealogia da TRC-001 devolve
 * as suas: um perfil que soma só os ingredientes declarados e cala sobre os demais parece completo
 * e não é. Perfil incompleto não vira alerta em algum canto da tela — ele barra o rótulo e barra a
 * troca de produto, porque "não sei" nunca pode valer como "não tem".
 */
public final class AllergenProfile {

    private final Set<AllergenCode> allergens;
    private final List<Gap> gaps;

    private AllergenProfile(Set<AllergenCode> allergens, List<Gap> gaps) {
        this.allergens = new TreeSet<>(allergens);
        this.gaps = List.copyOf(gaps);
    }

    /** Perfil de quem não tem composição alguma — sem alergênico e sem lacuna. */
    public static AllergenProfile empty() {
        return new AllergenProfile(Set.of(), List.of());
    }

    /**
     * Perfil que não pôde ser montado: lote sem receita publicada, composição indisponível.
     *
     * <p>Não é o mesmo que perfil vazio, e confundir os dois é o erro caro: vazio afirma isenção,
     * este afirma ignorância. A lacuna não aponta ingrediente porque o que falta é a composição.
     */
    public static AllergenProfile unknown(String reason) {
        return new AllergenProfile(Set.of(), List.of(new Gap(null, reason)));
    }

    public static AllergenProfile of(Collection<Contribution> contributions) {
        var allergens = new TreeSet<AllergenCode>();
        var gaps = new java.util.ArrayList<Gap>();
        for (var contribution : contributions) {
            if (contribution.declaration().declared()) {
                allergens.addAll(contribution.declaration().allergens());
            } else {
                gaps.add(new Gap(contribution.ingredientId(), contribution.label()));
            }
        }
        gaps.sort(Comparator.comparing(Gap::label, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(gap -> Objects.toString(gap.ingredientId(), "")));
        return new AllergenProfile(allergens, gaps);
    }

    /** Alergênicos declarados, ordenados. */
    public Set<AllergenCode> allergens() {
        return new TreeSet<>(allergens);
    }

    public List<Gap> gaps() {
        return gaps;
    }

    /** Se todo ingrediente da composição tem declaração — a condição para afirmar qualquer coisa. */
    public boolean complete() {
        return gaps.isEmpty();
    }

    /** O que este perfil carrega e {@code other} não aceita: a carga a remover numa troca. */
    public Set<AllergenCode> residueAgainst(AllergenProfile other) {
        var residue = new TreeSet<>(allergens);
        residue.removeAll(other.allergens);
        return residue;
    }

    /** Ingrediente da composição e a sua declaração, declarada ou faltante. */
    public record Contribution(UUID ingredientId, String label, AllergenDeclaration declaration) {
        public Contribution {
            Objects.requireNonNull(ingredientId, "ingredientId");
            Objects.requireNonNull(declaration, "declaração");
        }
    }

    /**
     * O motivo pelo qual o perfil não pode ser afirmado.
     *
     * @param ingredientId ingrediente sem declaração; {@code null} quando o que falta é a própria
     *                     composição, e portanto não há ingrediente a apontar
     */
    public record Gap(UUID ingredientId, String label) {
        public Gap {
            Objects.requireNonNull(label, "motivo da lacuna é obrigatório");
        }
    }
}
