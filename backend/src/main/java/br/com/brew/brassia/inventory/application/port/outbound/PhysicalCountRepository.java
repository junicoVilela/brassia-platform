package br.com.brew.brassia.inventory.application.port.outbound;

import br.com.brew.brassia.inventory.domain.PhysicalCount;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PhysicalCountRepository {
    void insert(PhysicalCount count, UUID createdBy);

    Optional<PhysicalCount> findById(UUID breweryId, UUID countId);

    List<PhysicalCount> findAll(UUID breweryId);

    /** Marca a contagem como aprovada (só de OPEN), atômico. Retorna false se já não era OPEN. */
    boolean markApproved(UUID breweryId, UUID countId, Instant approvedAt, UUID approvedBy);
}
