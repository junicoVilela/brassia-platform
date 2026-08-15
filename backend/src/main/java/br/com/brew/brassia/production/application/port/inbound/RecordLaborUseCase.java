package br.com.brew.brassia.production.application.port.inbound;

import br.com.brew.brassia.production.domain.LaborEntry;
import java.time.Instant;
import java.util.UUID;

/** Apontar horas trabalhadas num lote (CST-001-A). */
public interface RecordLaborUseCase {

    LaborEntry handle(Command command);

    record Command(UUID breweryId, UUID actorId, UUID batchId, String activity, Instant startedAt,
            Instant endedAt, int people) {}
}
