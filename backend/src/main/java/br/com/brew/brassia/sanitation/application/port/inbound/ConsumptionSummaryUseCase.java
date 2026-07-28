package br.com.brew.brassia.sanitation.application.port.inbound;

import br.com.brew.brassia.sanitation.domain.ConsumptionSummary;
import java.util.UUID;

public interface ConsumptionSummaryUseCase {
    ConsumptionSummary handle(UUID breweryId, String procedureCode);
}
