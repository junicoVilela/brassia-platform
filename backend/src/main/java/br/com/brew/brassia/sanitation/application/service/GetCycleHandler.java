package br.com.brew.brassia.sanitation.application.service;

import br.com.brew.brassia.sanitation.application.port.inbound.GetCycleUseCase;
import br.com.brew.brassia.sanitation.application.port.outbound.CleaningCycleRepository;
import br.com.brew.brassia.sanitation.domain.CleaningCycle;
import java.util.Objects;
import java.util.UUID;

public final class GetCycleHandler implements GetCycleUseCase {

    private final CleaningCycleRepository cycles;

    public GetCycleHandler(CleaningCycleRepository cycles) {
        this.cycles = Objects.requireNonNull(cycles);
    }

    @Override
    public CleaningCycle handle(UUID breweryId, UUID cycleId) {
        return cycles.findById(breweryId, cycleId)
                .orElseThrow(() -> new IllegalArgumentException("ciclo inexistente"));
    }
}
