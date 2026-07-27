package br.com.brew.brassia.production;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Evento de domínio: uma correção foi aplicada a um lote (CAL-002). Fato no
 * passado, consumível por outros módulos (ex.: custo, qualidade). Nenhuma ação
 * física — registra a decisão e o efeito planejado.
 */
public record CorrectionApplied(UUID breweryId, UUID batchId, UUID correctionId, String calculator,
        BigDecimal plannedValue, String plannedUnit, UUID actorId, Instant occurredAt) {}
