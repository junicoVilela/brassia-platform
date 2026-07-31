package br.com.brew.brassia.fermentation.application.service;

import br.com.brew.brassia.fermentation.application.port.inbound.GetYeastGenealogyUseCase;
import br.com.brew.brassia.fermentation.application.port.outbound.YeastHarvestRepository;
import br.com.brew.brassia.fermentation.domain.YeastHarvest;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class GetYeastGenealogyHandler implements GetYeastGenealogyUseCase {

    private final YeastHarvestRepository harvests;

    public GetYeastGenealogyHandler(YeastHarvestRepository harvests) {
        this.harvests = Objects.requireNonNull(harvests);
    }

    @Override
    public List<YeastHarvest> handle(UUID breweryId, UUID harvestId) {
        harvests.findById(breweryId, harvestId)
                .orElseThrow(() -> new IllegalArgumentException("coleta inexistente"));
        return harvests.findAncestry(breweryId, harvestId);
    }
}
