package br.com.brew.brassia.planning.application.port.outbound;

import br.com.brew.brassia.planning.domain.BrewOrder;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BrewOrderRepository {

    /**
     * Próximo número de sequência da OP para a cervejaria/ano, de forma atômica
     * (base do código único {@code OP-<ano>-<n>}), seguro sob concorrência.
     */
    long nextSequence(UUID breweryId, int year);

    void insert(BrewOrder order);

    List<BrewOrder> findPage(UUID breweryId, int page, int size);

    long count(UUID breweryId);

    Optional<BrewOrder> findById(UUID breweryId, UUID id);
}
