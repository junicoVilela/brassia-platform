package br.com.brew.brassia.foodsafety.application.port.inbound;

import br.com.brew.brassia.foodsafety.domain.Allergen;
import br.com.brew.brassia.foodsafety.domain.AllergenCode;
import br.com.brew.brassia.foodsafety.domain.AllergenProfile;
import br.com.brew.brassia.foodsafety.domain.ChangeoverVerdict;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Leituras da matriz de alergênicos (FDS-001). */
public interface AllergenQueries {

    List<Allergen> allergens(UUID breweryId);

    /**
     * A matriz inteira numa leitura: é assim que ela é usada — ninguém confere alergênico de um
     * ingrediente só, confere o cruzamento.
     */
    Matrix matrix(UUID breweryId);

    /** Perfil do lote, derivado da composição da receita publicada. */
    AllergenProfile batchProfile(UUID breweryId, UUID batchId);

    ChangeoverVerdict changeover(UUID breweryId, UUID equipmentId, UUID incomingBatchId, UUID previousBatchId,
            Instant previousUseAt, Instant at);

    /**
     * @param ingredients ingredientes com declaração — os que faltam na lista são a lacuna
     * @param dedications equipamentos com dedicação declarada; os demais são compartilhados
     * @param procedures  eficácia declarada por POP
     */
    record Matrix(List<Allergen> allergens, List<IngredientRow> ingredients, List<EquipmentRow> dedications,
            List<ProcedureRow> procedures) {}

    record IngredientRow(UUID ingredientId, String code, String name, boolean declared,
            Set<AllergenCode> allergens) {}

    record EquipmentRow(UUID equipmentId, Set<AllergenCode> allergens) {}

    record ProcedureRow(String procedureCode, Set<AllergenCode> allergens) {}
}
