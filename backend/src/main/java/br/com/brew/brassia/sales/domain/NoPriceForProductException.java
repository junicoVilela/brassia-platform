package br.com.brew.brassia.sales.domain;

import java.time.LocalDate;

/**
 * O produto não tem preço no canal, na data do pedido (SAL-002).
 *
 * <p>Recusar é o ponto: um pedido sem preço não tem total, e um total zero faria a venda sair de graça.
 * "Ainda não precificado" e "de graça" são coisas opostas, e tratá-las igual esconde a primeira.
 */
public class NoPriceForProductException extends RuntimeException {

    public NoPriceForProductException(String sku, LocalDate on) {
        super("o produto " + sku + " não tem preço neste canal em " + on);
    }
}
