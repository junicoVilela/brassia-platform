package br.com.brew.brassia.planning.domain;

/**
 * Ciclo de vida da ordem de produção (docs/03): DRAFT → RELEASED → IN_PRODUCTION
 * → FERMENTING → CONDITIONING → PACKAGED → CLOSED; CANCELLED é terminal (salvo
 * reabertura autorizada). Em BOP-001 a OP nasce em {@code DRAFT}; as transições
 * entram nas histórias seguintes (BOP-002 libera, BOP-003 cancela).
 */
public enum BrewOrderStatus {
    DRAFT,
    RELEASED,
    IN_PRODUCTION,
    FERMENTING,
    CONDITIONING,
    PACKAGED,
    CLOSED,
    CANCELLED
}
