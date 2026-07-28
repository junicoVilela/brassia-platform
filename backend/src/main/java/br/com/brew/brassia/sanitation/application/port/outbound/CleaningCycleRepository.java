package br.com.brew.brassia.sanitation.application.port.outbound;

import br.com.brew.brassia.sanitation.domain.CleaningCycle;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CleaningCycleRepository {
    void insert(CleaningCycle cycle);

    Optional<CleaningCycle> findById(UUID breweryId, UUID cycleId);

    /** Carrega o ciclo travando a linha (FOR UPDATE) para comandos concorrentes. */
    Optional<CleaningCycle> findForUpdate(UUID breweryId, UUID cycleId);

    /** Persiste o estado do ciclo e das suas etapas. */
    void update(CleaningCycle cycle);

    List<CleaningCycle> findAll(UUID breweryId);
}
