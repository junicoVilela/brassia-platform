package br.com.brew.brassia.fieldfeedback.domain;

/** Quão grave é a reclamação (FLD-001). */
public enum Severity {
    /** Preferência ou expectativa: a cerveja está conforme, o cliente esperava outra coisa. */
    PREFERENCE,
    /** Desvio perceptível que não afeta segurança. */
    QUALITY,
    /** Desvio que sugere falha de processo com alcance além deste exemplar. */
    SYSTEMIC,
    /** Risco à saúde: corpo estranho, contaminação, embalagem violada. */
    SAFETY
}
