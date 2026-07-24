package br.com.brew.brassia.catalog.application.port.outbound;

import br.com.brew.brassia.catalog.domain.IngredientTechnicalProfile;
import java.util.Optional;
import java.util.UUID;

public interface TechnicalProfileRepository {

    Optional<IngredientTechnicalProfile> findByIngredient(UUID breweryId, UUID ingredientId);

    /** Persiste o perfil e suas faixas no mesmo commit. */
    void insert(IngredientTechnicalProfile profile);

    /** Publica o perfil com trava otimista; falha se a versão não bater. */
    boolean markPublished(UUID breweryId, UUID ingredientId, long expectedVersion);
}
