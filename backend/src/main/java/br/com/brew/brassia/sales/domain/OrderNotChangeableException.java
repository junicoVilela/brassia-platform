package br.com.brew.brassia.sales.domain;

/**
 * Tentaram mexer num pedido que não aceita mais mudança (SAL-002).
 *
 * <p>Cancelar um pedido já atendido devolveria ao estoque unidades que saíram pela porta, e o estoque
 * passaria a contar cerveja que não existe. Cancelar um já cancelado é operação sem efeito que, se
 * passasse em silêncio, faria quem chamou acreditar que fez algo.
 */
public class OrderNotChangeableException extends RuntimeException {

    public OrderNotChangeableException(OrderStatus status) {
        super("este pedido está " + switch (status) {
            case CANCELLED -> "cancelado";
            case FULFILLED -> "atendido";
            case PLACED -> "confirmado";
        } + " e não aceita esta operação");
    }
}
