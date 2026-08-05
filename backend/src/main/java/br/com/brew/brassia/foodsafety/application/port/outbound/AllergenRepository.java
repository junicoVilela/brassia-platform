package br.com.brew.brassia.foodsafety.application.port.outbound;

import br.com.brew.brassia.foodsafety.domain.Allergen;
import br.com.brew.brassia.foodsafety.domain.AllergenCode;
import br.com.brew.brassia.foodsafety.domain.AllergenDeclaration;
import br.com.brew.brassia.foodsafety.domain.EquipmentDedication;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Persistência da matriz de alergênicos (FDS-001). Uma porta só para os três eixos: eles nascem,
 * mudam e são lidos juntos, e separá-los em três repositórios criaria fronteira onde não há.
 */
public interface AllergenRepository {

    List<Allergen> findAllergens(UUID breweryId);

    Optional<Allergen> findAllergen(UUID breweryId, AllergenCode code);

    void insertAllergen(Allergen allergen);

    /** Vazio significa "ninguém declarou" — o chamador converte em {@link AllergenDeclaration#missing}. */
    Optional<AllergenDeclaration> findDeclaration(UUID breweryId, UUID ingredientId);

    /** Só os ingredientes que têm declaração; os ausentes do mapa são as lacunas. */
    Map<UUID, AllergenDeclaration> findDeclarations(UUID breweryId, Collection<UUID> ingredientIds);

    List<AllergenDeclaration> findAllDeclarations(UUID breweryId);

    /** Regrava a declaração inteira: declarar é responder de novo, não acrescentar. */
    void saveDeclaration(UUID breweryId, AllergenDeclaration declaration, UUID actorId, Instant at);

    Optional<EquipmentDedication> findDedication(UUID breweryId, UUID equipmentId);

    List<EquipmentDedication> findDedications(UUID breweryId);

    void saveDedication(UUID breweryId, EquipmentDedication dedication, UUID actorId, Instant at);

    /** Remover a dedicação devolve o equipamento ao estado compartilhado. */
    void removeDedication(UUID breweryId, UUID equipmentId);

    Set<AllergenCode> findProcedureEffectiveness(UUID breweryId, String procedureCode);

    Map<String, Set<AllergenCode>> findAllProcedureEffectiveness(UUID breweryId);

    void saveProcedureEffectiveness(UUID breweryId, String procedureCode, Set<AllergenCode> allergens);
}
