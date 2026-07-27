package br.com.brew.brassia.planning;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Porta que o planejamento usa para reservar o estoque de uma OP inteira
 * (STK-003-A), declarada aqui por inversão de dependência: o módulo de estoque a
 * implementa, mantendo o sentido inventory → planning (já existente) sem ciclo.
 *
 * <p>Semântica all-or-nothing: se qualquer item não puder ser reservado por
 * completo, nada é reservado e a falta é reportada. É idempotente por OP —
 * libera as reservas atuais da referência antes de reservar de novo.
 */
public interface StockReservationGateway {

    Outcome reserveForOrder(UUID breweryId, UUID orderId, UUID actorId, List<MaterialLine> lines);

    /** Necessidade de um ingrediente (unidade canônica) para a reserva. */
    record MaterialLine(UUID ingredientId, BigDecimal quantity, String unit) {}

    /** Resultado: {@code reserved} verdadeiro só quando toda a OP foi reservada. */
    record Outcome(boolean reserved, List<Shortfall> shortfalls) {}

    /** Falta de um ingrediente: o quanto foi pedido e o quanto estava disponível. */
    record Shortfall(UUID ingredientId, BigDecimal requested, BigDecimal available, String unit) {}
}
