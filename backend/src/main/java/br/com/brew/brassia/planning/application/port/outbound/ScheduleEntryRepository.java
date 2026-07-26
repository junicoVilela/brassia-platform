package br.com.brew.brassia.planning.application.port.outbound;

import br.com.brew.brassia.planning.domain.ScheduleEntry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistência da agenda de produção. Todas as consultas são escopadas por
 * {@code brewery_id}.
 */
public interface ScheduleEntryRepository {

    void insert(ScheduleEntry entry);

    /**
     * Entradas existentes que sobrepõem a janela {@code [start, end)} no mesmo
     * equipamento da cervejaria (base do conflito de PLN-001). Janelas que apenas
     * se tocam não são retornadas.
     */
    List<Conflict> findEquipmentConflicts(UUID breweryId, UUID equipmentId, Instant start, Instant end);

    /** Entradas da cervejaria cuja janela intersecta o intervalo {@code [from, to)}. */
    List<ScheduleEntry> findBetween(UUID breweryId, Instant from, Instant to);

    Optional<ScheduleEntry> findById(UUID breweryId, UUID id);

    /** Resumo de uma entrada conflitante (para sinalização, sem carregar o agregado). */
    record Conflict(UUID entryId, Instant start, Instant end) {}
}
