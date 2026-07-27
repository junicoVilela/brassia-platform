package br.com.brew.brassia.production.application.port.inbound;

import br.com.brew.brassia.calculator.CalculatorEngine;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/** Pré-visualiza o impacto de uma correção no lote (PRD-004). Não persiste nada. */
public interface PreviewCorrectionUseCase {
    CalculatorEngine.Computation handle(Command command);

    record Command(UUID breweryId, UUID batchId, String calculator, Map<String, BigDecimal> inputs) {}
}
