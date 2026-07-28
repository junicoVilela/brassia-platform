package br.com.brew.brassia.sanitation;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento de domínio: um ciclo de limpeza foi verificado e liberado (CLN-004). Consumível
 * por outros módulos — o módulo de equipamento poderá desbloquear/marcar o equipamento
 * como limpo (débito CLN-004-A, quando houver estado de bloqueio de equipamento).
 */
public record CleaningCycleReleased(UUID breweryId, UUID cycleId, UUID equipmentId, String procedureCode,
        int procedureVersion, UUID actorId, Instant releasedAt) {}
