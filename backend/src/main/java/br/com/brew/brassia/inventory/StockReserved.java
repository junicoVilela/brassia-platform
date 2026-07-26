package br.com.brew.brassia.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Evento de domínio: estoque foi reservado para uma referência (ex.: ordem de
 * produção). Fato no passado, consumível por outros módulos.
 */
public record StockReserved(UUID breweryId, UUID reference, UUID ingredientId, BigDecimal quantity,
        String unit, Instant occurredAt) {}
