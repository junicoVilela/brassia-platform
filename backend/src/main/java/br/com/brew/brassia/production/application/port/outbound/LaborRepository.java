package br.com.brew.brassia.production.application.port.outbound;

import br.com.brew.brassia.production.domain.LaborEntry;
import java.util.List;
import java.util.UUID;

/** Apontamentos de hora trabalhada (CST-001-A). */
public interface LaborRepository {

    void insert(LaborEntry entry);

    List<LaborEntry> findByBatch(UUID breweryId, UUID batchId);
}
