package br.com.brew.brassia.production;

import java.util.UUID;

/**
 * Consulta publicada de existência de lote (PRD-001), para outros módulos vincularem
 * dados ao lote sem acessar a tabela de produção (ex.: leituras de fermentação, FER-002).
 */
public interface BatchLookup {
    boolean exists(UUID breweryId, UUID batchId);
}
