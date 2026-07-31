package br.com.brew.brassia.fermentation.application.service;

import br.com.brew.brassia.fermentation.application.port.inbound.ListYeastHarvestsUseCase;
import br.com.brew.brassia.fermentation.application.port.outbound.YeastHarvestRepository;
import br.com.brew.brassia.fermentation.domain.YeastHarvest;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ListYeastHarvestsHandler implements ListYeastHarvestsUseCase {

    private final YeastHarvestRepository harvests;

    public ListYeastHarvestsHandler(YeastHarvestRepository harvests) {
        this.harvests = Objects.requireNonNull(harvests);
    }

    @Override
    public List<YeastHarvest> handle(UUID breweryId, boolean onlyAvailable) {
        return harvests.findAll(breweryId, onlyAvailable);
    }
}
