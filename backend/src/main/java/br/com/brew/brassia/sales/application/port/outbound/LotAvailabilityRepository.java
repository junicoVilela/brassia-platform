package br.com.brew.brassia.sales.application.port.outbound;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Quantas unidades de cada lote ainda não estão reservadas (SAL-002).
 *
 * <p>Uma linha por lote, com total e reservado. É ela que impede vender duas vezes: {@link #reserve}
 * atualiza condicionalmente e devolve se coube — duas requisições simultâneas disputam a mesma linha, e a
 * segunda relê o valor já comitado pela primeira.
 */
public interface LotAvailabilityRepository {

    /** Cria a linha do lote na primeira vez que ele é reservado, com o total que ele tem. */
    void ensure(UUID breweryId, UUID finishedLotId, int totalUnits);

    /**
     * Tenta reservar. Devolve {@code false} quando não cabe — sem lançar, porque não caber é resposta
     * esperada e não excepcional: é o estoque acabando, que acontece todo dia.
     */
    boolean reserve(UUID breweryId, UUID finishedLotId, int units);

    /** Devolve unidades ao lote, no cancelamento. */
    void release(UUID breweryId, UUID finishedLotId, int units);

    /** Livre por lote: total menos reservado. */
    Map<UUID, Integer> freeUnits(UUID breweryId, Set<UUID> finishedLotIds);
}
