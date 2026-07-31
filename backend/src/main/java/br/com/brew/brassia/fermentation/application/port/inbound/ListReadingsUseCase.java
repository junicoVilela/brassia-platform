package br.com.brew.brassia.fermentation.application.port.inbound;

import br.com.brew.brassia.fermentation.domain.FermentationReading;
import java.util.List;
import java.util.UUID;

public interface ListReadingsUseCase {
    /** {@code kind} nulo/em branco retorna todas as grandezas do lote. */
    List<FermentationReading> handle(UUID breweryId, UUID batchId, String kind);
}
