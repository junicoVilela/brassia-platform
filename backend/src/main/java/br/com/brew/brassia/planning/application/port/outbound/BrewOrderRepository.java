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

    /**
     * Marca a OP como liberada (DRAFT → RELEASED), de forma atômica e guardada pelo
     * estado (fecha corrida de liberação concorrente). Retorna {@code false} se a OP
     * não estava mais em rascunho.
     */
    boolean markReleased(UUID breweryId, UUID id, UUID assignedUserId, java.time.Instant at);

    /**
     * Marca a OP como cancelada, atômico e guardado pelo estado (só DRAFT/RELEASED).
     * Retorna {@code false} se a OP não estava mais em um estado cancelável.
     */
    boolean markCancelled(UUID breweryId, UUID id, String reason, java.time.Instant at);

    List<BrewOrder> findPage(UUID breweryId, int page, int size);

    long count(UUID breweryId);

    Optional<BrewOrder> findById(UUID breweryId, UUID id);
}
