package br.com.brew.brassia.distribution.domain;

/**
 * O ciclo da carga.
 *
 * <p><strong>{@code PLANNED} e {@code RELEASED} são estados diferentes de propósito</strong>: entre um e
 * outro há uma pessoa que não é a que planejou. É a separação de deveres da história, e ela só existe
 * porque há dois estados para separar.
 */
public enum LoadStatus {
    /** Sendo montada. Ainda muda. */
    PLANNED,
    /** Conferida e liberada para sair — por outra pessoa. */
    RELEASED,
    /** Na rua. */
    IN_ROUTE,
    /** Voltou e foi encerrada. */
    CLOSED,
    CANCELLED
}
