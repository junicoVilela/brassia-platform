package br.com.brew.brassia.referencedata.domain;

/**
 * Estados do pipeline de importação (REF-002). Fluxo:
 * {@code RECEIVED → VALIDATING → REVIEW_REQUIRED → PUBLISHED}; {@code FAILED} é
 * terminal de falha. Estados terminais não transitam mais.
 */
public enum ImportJobStatus {
    RECEIVED,
    VALIDATING,
    REVIEW_REQUIRED,
    PUBLISHED,
    FAILED;

    public boolean isTerminal() {
        return this == PUBLISHED || this == FAILED;
    }
}
