package br.com.brew.brassia.sales.domain;

/**
 * O teto de crédito do cliente está numa moeda, e o pedido em outra (SAL-004).
 *
 * <p><strong>Recusar é a única resposta honesta.</strong> Não existe conversão sem taxa de câmbio, e
 * inventar uma faria o sistema decidir em nome da casa quanto vale o limite. As duas alternativas são
 * piores: deixar passar apaga o teto justamente para o cliente cuja configuração está errada — o
 * controle vira decoração no caso que mais precisa dele —, e somar os números como se fossem a mesma
 * moeda produz um limite que ninguém autorizou.
 *
 * <p><strong>Não é o mesmo erro que o do domínio de dinheiro.</strong> Antes desta exceção a soma
 * estourava lá dentro, e o vendedor recebia um `sales_currency_mismatch` genérico que não dizia o que
 * consertar. O problema é de <em>cadastro</em>: o teto foi registrado numa moeda que a lista de preço
 * daquele canal não usa, e é isso que a mensagem precisa apontar.
 */
public class CreditLimitCurrencyMismatchException extends RuntimeException {

    private final String ceilingCurrency;
    private final String orderCurrency;

    public CreditLimitCurrencyMismatchException(String ceilingCurrency, String orderCurrency) {
        super("o limite de crédito do cliente está em " + ceilingCurrency + " e o pedido em "
                + orderCurrency + "; não há como conferir o teto sem uma taxa de câmbio");
        this.ceilingCurrency = ceilingCurrency;
        this.orderCurrency = orderCurrency;
    }

    public String ceilingCurrency() {
        return ceilingCurrency;
    }

    public String orderCurrency() {
        return orderCurrency;
    }
}
