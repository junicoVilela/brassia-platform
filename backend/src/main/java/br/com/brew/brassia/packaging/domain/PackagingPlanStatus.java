package br.com.brew.brassia.packaging.domain;

/**
 * Ciclo de vida do plano de envase (PKG-001/PKG-003). O plano nasce em {@code PLANNED} (intenção);
 * {@code RESERVED} significa que a linha foi verificada e a embalagem está reservada;
 * {@code EXECUTED} é o envase realizado (PKG-003). {@code CANCELLED} é terminal e libera a reserva.
 *
 * <p>Os dois estados terminais não se equivalem: cancelado devolve a embalagem, executado a
 * consumiu. Por isso plano executado não é cancelável — desfazer produção não é cancelar plano.
 */
public enum PackagingPlanStatus {
    PLANNED,
    RESERVED,
    EXECUTED,
    CANCELLED;

    public boolean terminal() {
        return this == CANCELLED || this == EXECUTED;
    }
}
