package br.com.brew.brassia.referencedata.domain;

/**
 * Ciclo de vida do dataset no escopo do REF-001: rascunho e publicado.
 * Os estados de staging/validação ({@code RECEIVED → VALIDATING → REVIEW_REQUIRED})
 * pertencem ao pipeline do REF-002.
 */
public enum DatasetStatus {
    DRAFT,
    PUBLISHED
}
