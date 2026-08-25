package br.com.brew.brassia.production.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.production.application.port.inbound.CompleteBatchStepUseCase;
import br.com.brew.brassia.production.application.port.outbound.BatchRepository;
import br.com.brew.brassia.production.domain.Batch;
import br.com.brew.brassia.production.domain.BatchStatus;
import br.com.brew.brassia.production.domain.BatchStep;
import br.com.brew.brassia.production.domain.UnknownBatchException;
import br.com.brew.brassia.production.domain.UnknownStepException;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Conclui a etapa ativa do lote (PRD-002): valida que a etapa é a ATIVA (avanço
 * fora de ordem → 409), conclui e ativa a próxima. Sequência guardada também na
 * escrita (concorrência). Só lotes em andamento.
 */
public final class CompleteBatchStepHandler implements CompleteBatchStepUseCase {

    private final BatchRepository repository;
    private final AuditTrail audit;

    public CompleteBatchStepHandler(BatchRepository repository, AuditTrail audit) {
        this.repository = Objects.requireNonNull(repository);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Batch handle(Command command) {
        var batch = repository.findById(command.breweryId(), command.batchId())
                .orElseThrow(() -> new UnknownBatchException(command.batchId()));
        if (batch.status() != BatchStatus.IN_PROGRESS) {
            throw new IllegalStateException("lote não está em andamento");
        }

        var step = batch.steps().stream()
                .filter(s -> s.id().equals(command.stepId()))
                .findFirst()
                .orElseThrow(() -> new UnknownStepException(command.batchId(), command.stepId()));
        if (!step.isActive()) {
            throw new IllegalStateException("apenas a etapa ativa pode ser concluída");
        }

        var next = batch.steps().stream()
                .filter(s -> s.sequence() > step.sequence())
                .min((a, b) -> Integer.compare(a.sequence(), b.sequence()))
                .map(BatchStep::id)
                .orElse(null);

        var at = Instant.now();
        if (!repository.completeStep(command.breweryId(), command.batchId(), step.id(), next, at)) {
            throw new IllegalStateException("apenas a etapa ativa pode ser concluída");
        }

        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "production.step.complete",
                "production.batch", command.batchId().toString(),
                Map.of("step", step.type().name(), "sequence", String.valueOf(step.sequence()))));

        return repository.findById(command.breweryId(), command.batchId()).orElseThrow();
    }
}
