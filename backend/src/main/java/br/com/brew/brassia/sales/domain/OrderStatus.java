package br.com.brew.brassia.sales.domain;

/**
 * O que já aconteceu com o pedido (SAL-002).
 *
 * <p>Curto de propósito. Estado a mais é estado que alguém precisa manter, e cada um precisa responder a
 * uma pergunta que a operação faz de verdade.
 */
public enum OrderStatus {

    /**
     * Confirmado: reservou lote e prometeu data.
     *
     * <p>Não existe rascunho. Um pedido que não reservou nada não segura estoque, e chamar isso de
     * pedido faria a cervejaria contar como vendido o que qualquer outro cliente ainda pode levar.
     */
    PLACED,

    /** Cancelado: as reservas voltaram para o estoque. */
    CANCELLED,

    /** Atendido: o que foi reservado saiu. */
    FULFILLED
}
