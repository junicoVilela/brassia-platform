package br.com.brew.brassia.fermentation.application.port.outbound;

import br.com.brew.brassia.fermentation.domain.YeastHarvest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface YeastHarvestRepository {

    void insert(YeastHarvest harvest);

    /** Persiste o resultado da revisão (status + parecer); a coleta em si é imutável. */
    void updateReview(YeastHarvest harvest);

    Optional<YeastHarvest> findById(UUID breweryId, UUID harvestId);

    boolean existsByCode(UUID breweryId, String code);

    /** {@code onlyAvailable} restringe às aprovadas — as únicas reutilizáveis. */
    List<YeastHarvest> findAll(UUID breweryId, boolean onlyAvailable);

    /**
     * Cadeia da coleta até a origem comprada, da mais nova para a mais antiga. É a genealogia
     * completa exigida pela YST-001.
     */
    List<YeastHarvest> findAncestry(UUID breweryId, UUID harvestId);
}
