package br.com.brew.brassia.sales;

import java.time.Instant;
import java.util.UUID;

/**
 * Um pedido foi cancelado, e as reservas voltaram (INT-008).
 *
 * <p>É o gatilho para o e-commerce devolver o item à vitrine e para o fiscal saber que a nota não deve
 * sair. Sem ele, o cancelamento só existiria aqui dentro, e o produto continuaria anunciado como vendido
 * num lugar que a cervejaria não controla.
 */
public record SalesOrderCancelled(UUID breweryId, UUID orderId, String code, UUID customerId,
        Instant occurredAt) {}
