package br.com.brew.brassia.production.domain;

/** Ciclo de vida do lote de produção (PRD-001 cria em IN_PROGRESS; etapas seguintes evoluem). */
public enum BatchStatus {
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
