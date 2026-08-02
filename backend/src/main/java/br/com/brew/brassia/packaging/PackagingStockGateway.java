package br.com.brew.brassia.packaging;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Porta que o envase usa para reservar e devolver a embalagem do plano (PKG-001), declarada
 * aqui por inversão de dependência: o estoque a implementa, mantendo o sentido inventory →
 * packaging e evitando que o envase conheça lotes ou ledger.
 *
 * <p>É idempotente por plano: reservar de novo libera a reserva anterior da mesma referência
 * antes de reservar, então repetir o comando não duplica o compromisso.
 */
public interface PackagingStockGateway {

    Outcome reserve(UUID breweryId, UUID planId, UUID actorId, UUID containerId, BigDecimal units, String unit);

    /** Devolve ao estoque tudo o que o plano tinha reservado (cancelamento). */
    void release(UUID breweryId, UUID planId, UUID actorId);

    /**
     * @param reserved falso quando não havia embalagem suficiente; nada foi reservado
     * @param available o quanto existia disponível, na mesma unidade pedida
     */
    record Outcome(boolean reserved, BigDecimal available, String unit) {}
}
