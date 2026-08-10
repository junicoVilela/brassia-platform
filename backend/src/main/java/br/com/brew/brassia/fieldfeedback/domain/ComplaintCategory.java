package br.com.brew.brassia.fieldfeedback.domain;

/** Sobre o que é a reclamação (FLD-001). */
public enum ComplaintCategory {
    OFF_FLAVOR,
    APPEARANCE,
    CARBONATION,
    PACKAGING,
    /** Corpo estranho. Sozinha já implica risco, independentemente de quem classificou. */
    FOREIGN_BODY,
    /** Alegação de mal-estar. Idem. */
    ILLNESS,
    OTHER
}
