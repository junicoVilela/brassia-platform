package br.com.brew.brassia.water.application.port.outbound;

import br.com.brew.brassia.water.domain.WaterSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WaterSourceRepository {
    boolean existsByCode(UUID breweryId, String code);

    void insert(WaterSource source);

    boolean update(WaterSource source, long expectedVersion);

    Optional<WaterSource> findById(UUID breweryId, UUID id);

    List<WaterSource> findPage(UUID breweryId, int page, int size);

    long count(UUID breweryId);
}
