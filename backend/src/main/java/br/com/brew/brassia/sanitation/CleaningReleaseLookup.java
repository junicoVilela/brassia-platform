package br.com.brew.brassia.sanitation;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Consulta publicada da última liberação de limpeza de um equipamento (CLN-004), para outros
 * módulos condicionarem o uso à evidência de sanitização sem acessar a tabela de ciclos
 * (ex.: linha de envase, PKG-001). Só ciclo RELEASED conta: concluído sem verificação não libera.
 */
public interface CleaningReleaseLookup {

    Optional<Release> lastRelease(UUID breweryId, UUID equipmentId);

    record Release(UUID cycleId, String procedureCode, int procedureVersion, Instant releasedAt) {}
}
