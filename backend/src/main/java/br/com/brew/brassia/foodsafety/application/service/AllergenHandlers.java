package br.com.brew.brassia.foodsafety.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.catalog.IngredientPurchaseLookup;
import br.com.brew.brassia.equipment.EquipmentProfileLookup;
import br.com.brew.brassia.foodsafety.application.port.inbound.AllergenCommands;
import br.com.brew.brassia.foodsafety.application.port.outbound.AllergenRepository;
import br.com.brew.brassia.foodsafety.domain.Allergen;
import br.com.brew.brassia.foodsafety.domain.AllergenCode;
import br.com.brew.brassia.foodsafety.domain.AllergenDeclaration;
import br.com.brew.brassia.foodsafety.domain.EquipmentDedication;
import br.com.brew.brassia.foodsafety.domain.UnknownAllergenException;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Comandos da matriz de alergênicos (FDS-001).
 *
 * <p>Toda declaração é auditada com o conjunto declarado por extenso, e não só com "foi alterado":
 * meses depois, o que se precisa responder num recall é <em>quem afirmou o quê</em>, não que alguém
 * mexeu. Declarar alergênico é decisão de alçada, e o rastro é a metade dela.
 */
public final class AllergenHandlers {

    private AllergenHandlers() {
    }

    public static final class RegisterAllergen implements AllergenCommands.RegisterAllergen {

        private final AllergenRepository allergens;
        private final AuditTrail audit;

        public RegisterAllergen(AllergenRepository allergens, AuditTrail audit) {
            this.allergens = Objects.requireNonNull(allergens);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public Allergen handle(UUID actorId, UUID breweryId, String code, String name) {
            var allergen = Allergen.register(breweryId, code, name);
            if (allergens.findAllergen(breweryId, allergen.code()).isPresent()) {
                throw new IllegalStateException("alergênico já cadastrado: " + allergen.code());
            }
            allergens.insertAllergen(allergen);
            audit.record(AuditEvent.success(breweryId, actorId, "foodsafety.allergen.register",
                    "foodsafety.allergen", allergen.id().toString(),
                    Map.of("code", allergen.code().value(), "name", allergen.name())));
            return allergen;
        }
    }

    public static final class DeclareIngredient implements AllergenCommands.DeclareIngredient {

        private final AllergenRepository allergens;
        private final IngredientPurchaseLookup ingredients;
        private final AuditTrail audit;

        public DeclareIngredient(AllergenRepository allergens, IngredientPurchaseLookup ingredients,
                AuditTrail audit) {
            this.allergens = Objects.requireNonNull(allergens);
            this.ingredients = Objects.requireNonNull(ingredients);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public void handle(UUID actorId, UUID breweryId, UUID ingredientId, Set<String> allergenCodes) {
            var known = ingredients.findAll(breweryId).stream()
                    .anyMatch(ingredient -> ingredient.ingredientId().equals(ingredientId));
            if (!known) {
                throw new IllegalArgumentException("ingrediente inexistente nesta cervejaria");
            }
            var codes = validated(allergens, breweryId, allergenCodes);
            var at = Instant.now();
            allergens.saveDeclaration(breweryId,
                    AllergenDeclaration.declare(ingredientId, codes, actorId, at), actorId, at);
            audit.record(AuditEvent.success(breweryId, actorId, "foodsafety.allergen.declare-ingredient",
                    "catalog.ingredient", ingredientId.toString(),
                    Map.of("allergens", joined(codes))));
        }
    }

    public static final class DeclareDedication implements AllergenCommands.DeclareDedication {

        private final AllergenRepository allergens;
        private final EquipmentProfileLookup equipment;
        private final AuditTrail audit;

        public DeclareDedication(AllergenRepository allergens, EquipmentProfileLookup equipment, AuditTrail audit) {
            this.allergens = Objects.requireNonNull(allergens);
            this.equipment = Objects.requireNonNull(equipment);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public void handle(UUID actorId, UUID breweryId, UUID equipmentId, Set<String> allergenCodes) {
            if (equipment.find(breweryId, equipmentId).isEmpty()) {
                throw new IllegalArgumentException("equipamento inexistente nesta cervejaria");
            }
            var at = Instant.now();
            if (allergenCodes == null) {
                allergens.removeDedication(breweryId, equipmentId);
                audit.record(AuditEvent.success(breweryId, actorId, "foodsafety.allergen.share-equipment",
                        "equipment.equipment", equipmentId.toString(), Map.of("dedication", "removida")));
                return;
            }
            var codes = validated(allergens, breweryId, allergenCodes);
            allergens.saveDedication(breweryId, EquipmentDedication.of(equipmentId, codes), actorId, at);
            audit.record(AuditEvent.success(breweryId, actorId, "foodsafety.allergen.dedicate-equipment",
                    "equipment.equipment", equipmentId.toString(), Map.of("allergens", joined(codes))));
        }
    }

    public static final class DeclareProcedureEffectiveness
            implements AllergenCommands.DeclareProcedureEffectiveness {

        private final AllergenRepository allergens;
        private final AuditTrail audit;

        public DeclareProcedureEffectiveness(AllergenRepository allergens, AuditTrail audit) {
            this.allergens = Objects.requireNonNull(allergens);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public void handle(UUID actorId, UUID breweryId, String procedureCode, Set<String> allergenCodes) {
            if (procedureCode == null || procedureCode.isBlank()) {
                throw new IllegalArgumentException("código do POP é obrigatório");
            }
            var code = procedureCode.trim();
            var codes = validated(allergens, breweryId, allergenCodes);
            allergens.saveProcedureEffectiveness(breweryId, code, codes);
            audit.record(AuditEvent.success(breweryId, actorId, "foodsafety.allergen.declare-procedure",
                    "sanitation.procedure", code, Map.of("allergens", joined(codes))));
        }
    }

    /**
     * Código fora do vocabulário é recusado, e não silenciosamente ignorado: uma declaração aceita
     * pela metade deixaria o operador convencido de que declarou o que não declarou.
     */
    private static Set<AllergenCode> validated(AllergenRepository allergens, UUID breweryId,
            Collection<String> raw) {
        var codes = new LinkedHashSet<AllergenCode>();
        if (raw == null) {
            return codes;
        }
        for (var value : raw) {
            var code = AllergenCode.of(value);
            if (allergens.findAllergen(breweryId, code).isEmpty()) {
                throw new UnknownAllergenException(code);
            }
            codes.add(code);
        }
        return codes;
    }

    private static String joined(Set<AllergenCode> codes) {
        return String.join(",", new TreeSet<>(codes).stream().map(AllergenCode::value).toList());
    }
}
