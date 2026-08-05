package br.com.brew.brassia.foodsafety.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Declaração de alergênicos de um ingrediente (FDS-001).
 *
 * <p><strong>A ausência de declaração é um estado, não a ausência de alergênico.</strong> Um
 * ingrediente nunca declarado e um ingrediente declarado como isento produzem rótulos opostos e
 * decisões de troca opostas; representá-los pelo mesmo conjunto vazio faria a plataforma afirmar
 * isenção que ninguém assinou. Por isso {@link #missing(UUID)} existe e não é um construtor com
 * lista vazia.
 */
public final class AllergenDeclaration {

    private final UUID ingredientId;
    private final boolean declared;
    private final Set<AllergenCode> allergens;
    private final Instant declaredAt;
    private final UUID declaredBy;
    private final long version;

    private AllergenDeclaration(UUID ingredientId, boolean declared, Set<AllergenCode> allergens,
            Instant declaredAt, UUID declaredBy, long version) {
        this.ingredientId = Objects.requireNonNull(ingredientId, "ingredientId");
        this.declared = declared;
        this.allergens = allergens == null ? Set.of() : Set.copyOf(allergens);
        this.declaredAt = declaredAt;
        this.declaredBy = declaredBy;
        this.version = version;
        if (!declared && !this.allergens.isEmpty()) {
            throw new IllegalStateException("alergênico sem declaração não tem quem responda por ele");
        }
    }

    /** Ingrediente sobre o qual ninguém respondeu ainda — uma lacuna, e é assim que ela viaja. */
    public static AllergenDeclaration missing(UUID ingredientId) {
        return new AllergenDeclaration(ingredientId, false, Set.of(), null, null, 0);
    }

    /** Declaração assinada; conjunto vazio significa "declarado isento", que é uma afirmação. */
    public static AllergenDeclaration declare(UUID ingredientId, Set<AllergenCode> allergens, UUID actorId,
            Instant at) {
        return new AllergenDeclaration(ingredientId, true, allergens,
                Objects.requireNonNull(at, "instante da declaração"),
                Objects.requireNonNull(actorId, "autor da declaração"), 0);
    }

    public static AllergenDeclaration reconstitute(UUID ingredientId, Set<AllergenCode> allergens,
            Instant declaredAt, UUID declaredBy, long version) {
        return new AllergenDeclaration(ingredientId, true, allergens, declaredAt, declaredBy, version);
    }

    public UUID ingredientId() { return ingredientId; }
    public boolean declared() { return declared; }
    public Instant declaredAt() { return declaredAt; }
    public UUID declaredBy() { return declaredBy; }
    public long version() { return version; }

    /** Ordenado: o rótulo e o veredito de troca precisam sair iguais a cada leitura. */
    public Set<AllergenCode> allergens() {
        return new TreeSet<>(allergens);
    }

    public boolean contains(AllergenCode code) {
        return allergens.contains(code);
    }
}
