package br.com.brew.brassia.packaging.application.port.inbound;

import br.com.brew.brassia.packaging.domain.FinishedLot;
import java.util.List;
import java.util.UUID;

/** Consulta dos lotes de produto acabado (TRC-001-B). Não há comando: eles nascem do envase. */
public interface FinishedLotQueries {

    List<FinishedLot> all(UUID breweryId);

    List<FinishedLot> byBatch(UUID breweryId, UUID batchId);
}
