package br.com.brew.brassia.packaging.domain;

/**
 * Ciclo de vida do plano de envase (PKG-001). O plano nasce em {@code PLANNED} (intenção);
 * {@code RESERVED} significa que a linha foi verificada e a embalagem está reservada — é o
 * estado que PKG-003 vai consumir para executar. {@code CANCELLED} é terminal e libera a reserva.
 */
public enum PackagingPlanStatus {
    PLANNED,
    RESERVED,
    CANCELLED;

    public boolean terminal() {
        return this == CANCELLED;
    }
}
