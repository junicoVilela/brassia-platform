package br.com.brew.brassia.equipment.application.port.outbound;

import br.com.brew.brassia.equipment.domain.EquipmentMaintenance;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MaintenanceRepository {
    void insert(EquipmentMaintenance maintenance);

    /** Existe janela SCHEDULED do equipamento que sobrepõe [from, to)? */
    boolean hasScheduledOverlap(UUID breweryId, UUID equipmentId, Instant from, Instant to);

    Optional<EquipmentMaintenance> findById(UUID breweryId, UUID equipmentId, UUID id);

    /** Atualiza status com lock otimista. */
    boolean updateStatus(EquipmentMaintenance maintenance, long expectedVersion);

    List<EquipmentMaintenance> findByEquipment(UUID breweryId, UUID equipmentId);
}
