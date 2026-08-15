package br.com.brew.brassia.sales.application.port.outbound;

import br.com.brew.brassia.sales.SalesOrderCancelled;
import br.com.brew.brassia.sales.SalesOrderFulfilled;
import br.com.brew.brassia.sales.SalesOrderPlaced;

/**
 * Publica os fatos do pedido (INT-008).
 *
 * <p>Porta de saída, e não chamada direta ao Spring: o caso de uso não precisa saber que existe um
 * publicador de eventos, e o teste de domínio não precisa de contexto para rodar.
 */
public interface SalesOrderEventPublisher {

    void publish(SalesOrderPlaced event);

    void publish(SalesOrderCancelled event);

    void publish(SalesOrderFulfilled event);
}
