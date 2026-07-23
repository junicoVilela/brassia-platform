package br.com.brew.brassia.water.application.port.outbound;

import br.com.brew.brassia.water.domain.WaterProfile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WaterProfileRepository {
    boolean existsByCode(UUID breweryId, String code);

    void insert(WaterProfile profile);

    boolean update(WaterProfile profile, long expectedVersion);

    Optional<WaterProfile> findById(UUID breweryId, UUID id);

    List<WaterProfile> findPage(UUID breweryId, int page, int size);

    long count(UUID breweryId);
}
