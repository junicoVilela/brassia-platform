package br.com.brew.brassia.production.application.service;

import br.com.brew.brassia.calculator.CalculatorEngine;
import br.com.brew.brassia.production.application.port.inbound.PreviewCorrectionUseCase;
import br.com.brew.brassia.production.application.port.outbound.BatchRepository;
import br.com.brew.brassia.production.domain.BatchStatus;
import br.com.brew.brassia.production.domain.BrewCorrections;
import br.com.brew.brassia.production.domain.UnknownBatchException;
import java.util.Objects;

/**
 * Pré-visualiza o impacto de uma correção (PRD-004): valida o lote (em andamento)
 * e restringe às correções de brassa; delega o cálculo ao motor versionado. Não
 * persiste — nenhuma correção é aplicada sem confirmação (aplicação é CAL-002).
 */
public final class PreviewCorrectionHandler implements PreviewCorrectionUseCase {

    private final BatchRepository batches;
    private final CalculatorEngine engine;

    public PreviewCorrectionHandler(BatchRepository batches, CalculatorEngine engine) {
        this.batches = Objects.requireNonNull(batches);
        this.engine = Objects.requireNonNull(engine);
    }

    @Override
    public CalculatorEngine.Computation handle(Command command) {
        var batch = batches.findById(command.breweryId(), command.batchId())
                .orElseThrow(() -> new UnknownBatchException(command.batchId()));
        if (batch.status() != BatchStatus.IN_PROGRESS) {
            throw new IllegalStateException("lote não está em andamento");
        }
        if (!BrewCorrections.isCorrection(command.calculator())) {
            throw new IllegalArgumentException("correção de brassa desconhecida: " + command.calculator());
        }
        return engine.compute(command.calculator(), command.inputs());
    }
}
