package br.com.brew.brassia.sales;

import java.time.Instant;
import java.util.UUID;

/**
 * O pedido foi atendido: o que estava reservado saiu (INT-008).
 *
 * <p>É o fato que o contábil espera para reconhecer a receita. A plataforma **não** reconhece nada — ela
 * avisa que aconteceu, e quem faz contabilidade faz contabilidade.
 */
public record SalesOrderFulfilled(UUID breweryId, UUID orderId, String code, UUID customerId,
        Instant occurredAt) {}
