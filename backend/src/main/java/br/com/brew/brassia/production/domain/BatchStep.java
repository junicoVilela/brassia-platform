package br.com.brew.brassia.production.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Uma etapa do roteiro do lote (PRD-001/PRD-002). Estado sequencial
 * (PENDING → ACTIVE → DONE) com marcos de tempo server-aware ({@code startedAt}
 * ao ativar, {@code completedAt} ao concluir) — o cronômetro deriva desses.
 */
public record BatchStep(UUID id, int sequence, BatchStepType type, String label, BatchStepStatus status,
        Instant startedAt, Instant completedAt) {

    public BatchStep {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(status, "status");
        if (sequence < 0) {
            throw new IllegalArgumentException("sequência não pode ser negativa");
        }
        label = label == null || label.isBlank() ? type.name() : label.trim();
    }

    /** Etapa recém-criada (pendente, sem marcos). */
    public static BatchStep of(int sequence, BatchStepType type, String label) {
        return new BatchStep(UUID.randomUUID(), sequence, type, label, BatchStepStatus.PENDING, null, null);
    }

    public boolean isActive() {
        return status == BatchStepStatus.ACTIVE;
    }

    /** Ativa a etapa (PENDING → ACTIVE), marcando o início. */
    public BatchStep activate(Instant at) {
        return new BatchStep(id, sequence, type, label, BatchStepStatus.ACTIVE, at, completedAt);
    }

    /** Conclui a etapa (ACTIVE → DONE), marcando o fim. */
    public BatchStep complete(Instant at) {
        return new BatchStep(id, sequence, type, label, BatchStepStatus.DONE, startedAt, at);
    }
}
