package br.com.brew.brassia.equipment.application.port.outbound;

import br.com.brew.brassia.equipment.domain.Equipment;
import br.com.brew.brassia.equipment.domain.EquipmentSnapshot;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EquipmentRepository {
    boolean existsByCode(UUID breweryId, String code);

    void insert(Equipment equipment);

    /** Atualização com lock otimista: só afeta a linha se a versão bater. */
    boolean update(Equipment equipment, long expectedVersion);

    /** Registra o snapshot imutável de uma versão (histórico append-only). */
    void appendRevision(EquipmentSnapshot snapshot, UUID recordedBy);

    Optional<Equipment> findById(UUID breweryId, UUID id);

    Optional<EquipmentSnapshot> findRevision(UUID breweryId, UUID id, long version);

    List<Equipment> findPage(UUID breweryId, int page, int size);

    long count(UUID breweryId);
}
