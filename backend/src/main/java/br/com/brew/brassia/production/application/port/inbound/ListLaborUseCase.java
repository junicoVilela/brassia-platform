package br.com.brew.brassia.production.application.port.inbound;

import br.com.brew.brassia.production.domain.LaborEntry;
import java.util.List;
import java.util.UUID;

/** Os apontamentos de hora de um lote (CST-001-A). */
public interface ListLaborUseCase {

    List<LaborEntry> handle(UUID breweryId, UUID batchId);
}
