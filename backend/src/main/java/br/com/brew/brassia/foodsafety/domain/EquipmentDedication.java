package br.com.brew.brassia.foodsafety.domain;

import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Dedicação declarada de um equipamento (FDS-001).
 *
 * <p>Um equipamento é <strong>compartilhado</strong> por omissão — é o estado natural de quase toda
 * cervejaria, e é o que torna a troca de produto uma decisão de segurança em vez de rotina. Declarar
 * dedicação é afirmar o contrário: aquele equipamento só roda produtos dentro daquele perfil.
 *
 * <p>Dedicação com conjunto vazio é a linha <em>livre de alergênicos</em>, a mais restritiva que
 * existe: nenhuma limpeza a rescue, porque a garantia ali não é o procedimento, é o fato de o
 * alergênico nunca ter entrado.
 */
public final class EquipmentDedication {

    private final UUID equipmentId;
    private final Set<AllergenCode> allergens;

    private EquipmentDedication(UUID equipmentId, Set<AllergenCode> allergens) {
        this.equipmentId = Objects.requireNonNull(equipmentId, "equipmentId");
        this.allergens = allergens == null ? Set.of() : Set.copyOf(allergens);
    }

    public static EquipmentDedication of(UUID equipmentId, Set<AllergenCode> allergens) {
        return new EquipmentDedication(equipmentId, allergens);
    }

    public UUID equipmentId() {
        return equipmentId;
    }

    public Set<AllergenCode> allergens() {
        return new TreeSet<>(allergens);
    }

    /** O que o perfil traz e a dedicação não admite; vazio significa que o equipamento aceita. */
    public Set<AllergenCode> rejected(AllergenProfile incoming) {
        var rejected = new TreeSet<>(incoming.allergens());
        rejected.removeAll(allergens);
        return rejected;
    }
}
