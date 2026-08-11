package br.com.brew.brassia.production.domain;

/**
 * Como o lote chegou ao tanque (PRD-005; enchimento de blend a partir da DEC-BLD-003).
 *
 * <p>Os dois ocupam um vaso e determinam o volume envasável — por isso moram na mesma tabela, e por isso
 * a ocupação continua tendo uma única resposta. O que os separa é o que se sabe: a transferência de brassa
 * traz OG medido, o enchimento de blend não tem OG nenhum para trazer.
 */
public enum TransferKind {

    /** O mosto do dia de brassa indo para o fermentador, com OG medido. */
    BREW_TRANSFER,

    /** Cerveja pronta enchendo o tanque do lote que o blend produziu. Não tem OG: ninguém mediu. */
    BLEND_FILL
}
