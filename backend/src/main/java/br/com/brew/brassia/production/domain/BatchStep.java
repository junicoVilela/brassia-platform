package br.com.brew.brassia.production.domain;

import java.util.Objects;
import java.util.UUID;

/** Uma etapa do roteiro do lote (PRD-001). A navegação passo a passo é PRD-002. */
public record BatchStep(UUID id, int sequence, BatchStepType type, String label) {

    public BatchStep {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        if (sequence < 0) {
            throw new IllegalArgumentException("sequência não pode ser negativa");
        }
        label = label == null || label.isBlank() ? type.name() : label.trim();
    }

    public static BatchStep of(int sequence, BatchStepType type, String label) {
        return new BatchStep(UUID.randomUUID(), sequence, type, label);
    }
}
