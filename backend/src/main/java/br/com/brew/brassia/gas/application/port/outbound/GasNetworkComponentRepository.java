package br.com.brew.brassia.gas.application.port.outbound;

import br.com.brew.brassia.gas.domain.ComponentKind;
import br.com.brew.brassia.gas.domain.GasNetworkComponent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GasNetworkComponentRepository {

    void insert(GasNetworkComponent component);

    Optional<GasNetworkComponent> findById(UUID breweryId, UUID componentId);

    List<GasNetworkComponent> findAll(UUID breweryId, ComponentKind kind);

    boolean existsByCode(UUID breweryId, String code);

    boolean update(GasNetworkComponent component, long expectedVersion);
}
