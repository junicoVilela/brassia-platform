package br.com.brew.brassia.container.domain;

/** Onde o vasilhame está fisicamente. Grosso de propósito: a rota fina é da LOG-001. */
public enum LocationKind {
    /** Num depósito da casa. */
    WAREHOUSE,
    /** Na rua, entre dois pontos. */
    IN_TRANSIT,
    /** Com um cliente. */
    CUSTOMER,
    /** Em terceiro — oficina, lavador, pool. */
    THIRD_PARTY
}
