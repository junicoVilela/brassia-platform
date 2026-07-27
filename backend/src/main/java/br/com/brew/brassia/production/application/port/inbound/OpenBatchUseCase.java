package br.com.brew.brassia.production.application.port.inbound;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Abre o lote de produção ao iniciar uma OP (PRD-001). Idempotente por OP: se o
 * lote já existe, não cria de novo.
 */
public interface OpenBatchUseCase {
    void handle(Command command);

    record Command(UUID breweryId, UUID orderId, String code, UUID recipeId, int recipeVersion, String recipeName,
            BigDecimal volumeLiters, UUID actorId) {}
}
