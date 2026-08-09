package br.com.brew.brassia.ai.domain;

import java.util.UUID;

/** O lote a avaliar não existe nesta cervejaria (AIA-002). */
public final class UnknownBatchException extends RuntimeException {

    private final UUID batchId;

    public UnknownBatchException(UUID batchId) {
        super("este lote não existe nesta cervejaria");
        this.batchId = batchId;
    }

    public UUID batchId() {
        return batchId;
    }
}
