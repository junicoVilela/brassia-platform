package br.com.brew.brassia.foodsafety.application.service;

import br.com.brew.brassia.catalog.IngredientPurchaseLookup;
import br.com.brew.brassia.foodsafety.application.port.inbound.AllergenQueries;
import br.com.brew.brassia.foodsafety.application.port.outbound.AllergenRepository;
import br.com.brew.brassia.foodsafety.domain.Allergen;
import br.com.brew.brassia.foodsafety.domain.AllergenDeclaration;
import br.com.brew.brassia.foodsafety.domain.AllergenProfile;
import br.com.brew.brassia.foodsafety.domain.Changeover;
import br.com.brew.brassia.foodsafety.domain.ChangeoverVerdict;
import br.com.brew.brassia.production.BatchLookup;
import br.com.brew.brassia.recipe.RecipeLookup;
import br.com.brew.brassia.sanitation.CleaningReleaseLookup;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Leituras da matriz de alergênicos (FDS-001).
 *
 * <p>O perfil de um lote é <strong>derivado</strong>, não guardado: ele é a soma das declarações
 * dos ingredientes da receita publicada daquele lote, lida no momento da pergunta. Materializá-lo
 * criaria uma segunda verdade que continuaria dizendo "isento" depois de alguém declarar o
 * alergênico do malte — o mesmo motivo pelo qual a genealogia da TRC-001 também é derivada.
 *
 * <p>A composição vem da receita <em>publicada</em>, que é imutável e versionada; é o que permite
 * explicar meses depois por que aquele rótulo saiu com aquele texto.
 */
public final class AllergenQueryHandler implements AllergenQueries {

    private final AllergenRepository allergens;
    private final BatchLookup batches;
    private final RecipeLookup recipes;
    private final IngredientPurchaseLookup ingredients;
    private final CleaningReleaseLookup cleanings;

    public AllergenQueryHandler(AllergenRepository allergens, BatchLookup batches, RecipeLookup recipes,
            IngredientPurchaseLookup ingredients, CleaningReleaseLookup cleanings) {
        this.allergens = Objects.requireNonNull(allergens);
        this.batches = Objects.requireNonNull(batches);
        this.recipes = Objects.requireNonNull(recipes);
        this.ingredients = Objects.requireNonNull(ingredients);
        this.cleanings = Objects.requireNonNull(cleanings);
    }

    @Override
    public List<Allergen> allergens(UUID breweryId) {
        return allergens.findAllergens(breweryId);
    }

    @Override
    public Matrix matrix(UUID breweryId) {
        var declarations = allergens.findAllDeclarations(breweryId).stream()
                .collect(Collectors.toMap(AllergenDeclaration::ingredientId, declaration -> declaration));
        var rows = new ArrayList<IngredientRow>();
        for (var ingredient : ingredients.findAll(breweryId)) {
            var declaration = declarations.get(ingredient.ingredientId());
            rows.add(new IngredientRow(ingredient.ingredientId(), ingredient.code(), ingredient.name(),
                    declaration != null,
                    declaration == null ? Set.of() : declaration.allergens()));
        }
        var dedications = allergens.findDedications(breweryId).stream()
                .map(dedication -> new EquipmentRow(dedication.equipmentId(), dedication.allergens()))
                .toList();
        var procedures = allergens.findAllProcedureEffectiveness(breweryId).entrySet().stream()
                .map(entry -> new ProcedureRow(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(ProcedureRow::procedureCode))
                .toList();
        return new Matrix(allergens.findAllergens(breweryId), rows, dedications, procedures);
    }

    @Override
    public AllergenProfile batchProfile(UUID breweryId, UUID batchId) {
        var batch = batches.find(breweryId, batchId);
        if (batch.isEmpty()) {
            return AllergenProfile.unknown("lote inexistente nesta cervejaria");
        }
        var composition = recipes.findPublishedComposition(breweryId, batch.get().recipeId());
        if (composition.isEmpty()) {
            return AllergenProfile.unknown("a receita do lote não tem composição publicada");
        }

        var ingredientIds = composition.get().items().stream()
                .map(RecipeLookup.CompositionItem::ingredientId)
                .distinct()
                .toList();
        var declarations = allergens.findDeclarations(breweryId, ingredientIds);
        var names = ingredientNames(breweryId);

        var contributions = new ArrayList<AllergenProfile.Contribution>();
        for (var ingredientId : ingredientIds) {
            var declaration = declarations.getOrDefault(ingredientId, AllergenDeclaration.missing(ingredientId));
            contributions.add(new AllergenProfile.Contribution(ingredientId,
                    names.getOrDefault(ingredientId, ingredientId.toString()), declaration));
        }
        return AllergenProfile.of(contributions);
    }

    @Override
    public ChangeoverVerdict changeover(UUID breweryId, UUID equipmentId, UUID incomingBatchId,
            UUID previousBatchId, Instant previousUseAt, Instant at) {
        // Cervejaria que não cadastrou alergênico nenhum não está usando a matriz, e a troca não é
        // avaliada — mesmo princípio da validade do CIP (PRM-001), que sem prazo configurado não
        // expira. Ganhar a funcionalidade não pode parar a linha de quem não pediu por ela; a
        // matriz liga no instante em que a casa cadastra o primeiro alergênico, que é a adesão.
        if (allergens.findAllergens(breweryId).isEmpty()) {
            return new ChangeoverVerdict(ChangeoverVerdict.Outcome.CLEAR,
                    "A cervejaria não cadastrou alergênicos: a matriz não está em uso.", Set.of(), List.of());
        }
        var incoming = batchProfile(breweryId, incomingBatchId);
        var previous = previousBatchId == null ? null : batchProfile(breweryId, previousBatchId);
        var dedication = allergens.findDedication(breweryId, equipmentId).orElse(null);
        var evidence = cleanings.lastRelease(breweryId, equipmentId)
                .map(release -> new Changeover.CleaningEvidence(release.procedureCode(), release.releasedAt(),
                        allergens.findProcedureEffectiveness(breweryId, release.procedureCode())))
                .orElse(null);
        return Changeover.assess(incoming, previous, previousUseAt, dedication, evidence, at);
    }

    private Map<UUID, String> ingredientNames(UUID breweryId) {
        var names = new HashMap<UUID, String>();
        for (var ingredient : ingredients.findAll(breweryId)) {
            names.put(ingredient.ingredientId(), ingredient.name());
        }
        return names;
    }
}
