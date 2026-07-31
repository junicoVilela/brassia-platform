package br.com.brew.brassia.fermentation.application.port.inbound;

import br.com.brew.brassia.fermentation.domain.YeastHarvest;
import java.util.List;
import java.util.UUID;

/** Genealogia completa de uma coleta (YST-001): dela até a levedura comprada. */
public interface GetYeastGenealogyUseCase {
    List<YeastHarvest> handle(UUID breweryId, UUID harvestId);
}
