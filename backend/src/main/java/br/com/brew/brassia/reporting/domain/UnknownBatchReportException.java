package br.com.brew.brassia.reporting.domain;

import java.util.UUID;

/** Lote inexistente nesta cervejaria: o relatório recusa em vez de devolver um dossiê vazio. */
public class UnknownBatchReportException extends RuntimeException {

    private final UUID batchId;

    public UnknownBatchReportException(UUID batchId) {
        super("lote inexistente nesta cervejaria: " + batchId);
        this.batchId = batchId;
    }

    public UUID batchId() {
        return batchId;
    }
}
