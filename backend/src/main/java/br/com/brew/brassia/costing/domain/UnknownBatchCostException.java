package br.com.brew.brassia.costing.domain;

import java.util.Objects;
import java.util.UUID;

/** Lote inexistente nesta cervejaria, do ponto de vista do custo (CST-001). */
public final class UnknownBatchCostException extends RuntimeException {

    private final transient UUID batchId;

    public UnknownBatchCostException(UUID batchId) {
        super("lote inexistente");
        this.batchId = Objects.requireNonNull(batchId);
    }

    public UUID batchId() {
        return batchId;
    }
}
