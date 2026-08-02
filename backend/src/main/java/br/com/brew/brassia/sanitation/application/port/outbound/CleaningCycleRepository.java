package br.com.brew.brassia.sanitation.application.port.outbound;

import br.com.brew.brassia.sanitation.CleaningReleaseLookup;
import br.com.brew.brassia.sanitation.domain.CleaningCycle;
import br.com.brew.brassia.sanitation.domain.ConsumptionSummary;
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

    /** Resumo consultivo de consumo por código de POP (CLN-005). */
    ConsumptionSummary summarizeConsumption(UUID breweryId, String procedureCode);

    /** Última liberação (RELEASED) do equipamento, para outros módulos exigirem evidência de limpeza. */
    Optional<CleaningReleaseLookup.Release> findLastRelease(UUID breweryId, UUID equipmentId);
}
