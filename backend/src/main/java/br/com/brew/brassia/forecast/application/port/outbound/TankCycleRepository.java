package br.com.brew.brassia.forecast.application.port.outbound;

import java.util.List;
import java.util.UUID;

/** Os ciclos de ocupação declarados pela casa (DUV-FCST-001). */
public interface TankCycleRepository {

    void save(UUID breweryId, UUID equipmentId, int cycleDays, String note, UUID actor);

    void remove(UUID breweryId, UUID equipmentId);

    /** Vazio é o estado inicial, e legítimo: sem declaração não há capacidade a afirmar. */
    List<TankCycle> cycles(UUID breweryId);

    record TankCycle(UUID equipmentId, int cycleDays, String note) {}
}
