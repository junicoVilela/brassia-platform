package br.com.brew.brassia.sanitation.application.service;

import br.com.brew.brassia.sanitation.application.port.inbound.ConsumptionSummaryUseCase;
import br.com.brew.brassia.sanitation.application.port.outbound.CleaningCycleRepository;
import br.com.brew.brassia.sanitation.domain.ConsumptionSummary;
import java.util.Objects;
import java.util.UUID;

/** Comparação consultiva de consumo por POP (CLN-005); não altera parâmetros do POP. */
public final class ConsumptionSummaryHandler implements ConsumptionSummaryUseCase {

    private final CleaningCycleRepository cycles;

    public ConsumptionSummaryHandler(CleaningCycleRepository cycles) {
        this.cycles = Objects.requireNonNull(cycles);
    }

    @Override
    public ConsumptionSummary handle(UUID breweryId, String procedureCode) {
        if (procedureCode == null || procedureCode.isBlank()) {
            throw new IllegalArgumentException("código do POP é obrigatório");
        }
        return cycles.summarizeConsumption(breweryId, procedureCode.trim());
    }
}
