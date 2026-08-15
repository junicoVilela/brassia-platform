package br.com.brew.brassia.sales.application.port.outbound;

import br.com.brew.brassia.sales.domain.SalesOrder;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SalesOrderRepository {

    void insert(SalesOrder order, UUID actorId, String idempotencyKey);

    void updateStatusAndPromise(SalesOrder order);

    Optional<SalesOrder> find(UUID breweryId, UUID id);

    List<SalesOrder> list(UUID breweryId);

    /** O pedido já criado com esta chave, se houver — é o que torna o registro idempotente. */
    Optional<SalesOrder> findByIdempotencyKey(UUID breweryId, String key);
}
