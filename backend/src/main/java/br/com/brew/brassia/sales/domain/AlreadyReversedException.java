package br.com.brew.brassia.sales.domain;

/**
 * Este recebimento já foi estornado.
 *
 * <p>A garantia é o índice único parcial; isto é a tradução dele. Estornar duas vezes o mesmo lançamento
 * tiraria da conta um dinheiro que só entrou uma vez — e o cliente ganharia limite que não tem.
 */
public class AlreadyReversedException extends RuntimeException {

    public AlreadyReversedException() {
        super("Este recebimento já foi estornado.");
    }
}
