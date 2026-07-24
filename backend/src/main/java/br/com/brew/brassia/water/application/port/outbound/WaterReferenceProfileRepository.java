package br.com.brew.brassia.water.application.port.outbound;

import br.com.brew.brassia.water.domain.WaterReferenceProfile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WaterReferenceProfileRepository {

    boolean existsByNameEdition(UUID breweryId, String name, String edition);

    void insert(WaterReferenceProfile profile);

    /** Perfil visível à cervejaria: o próprio ou um global. */
    Optional<WaterReferenceProfile> findVisible(UUID breweryId, UUID id);

    /** Perfis globais somados aos da cervejaria, paginados. */
    List<WaterReferenceProfile> findPage(UUID breweryId, int page, int size);

    long count(UUID breweryId);

    boolean markPublished(UUID id, long expectedVersion);
}
