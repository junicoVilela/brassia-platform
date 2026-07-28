package br.com.brew.brassia.sanitation.application.service;

import br.com.brew.brassia.sanitation.application.port.inbound.ListCyclesUseCase;
import br.com.brew.brassia.sanitation.application.port.outbound.CleaningCycleRepository;
import br.com.brew.brassia.sanitation.domain.CleaningCycle;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ListCyclesHandler implements ListCyclesUseCase {

    private final CleaningCycleRepository cycles;

    public ListCyclesHandler(CleaningCycleRepository cycles) {
        this.cycles = Objects.requireNonNull(cycles);
    }

    @Override
    public List<CleaningCycle> handle(UUID breweryId) {
        return cycles.findAll(breweryId);
    }
}
