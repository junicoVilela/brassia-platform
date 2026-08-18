package br.com.brew.brassia.sales.domain;

import java.math.BigDecimal;

/**
 * O recebimento é maior do que o pedido ainda deve.
 *
 * <p><strong>Recusar aqui é o que pega o zero a mais.</strong> Lançar R$ 12.000 num pedido de R$ 1.200 é
 * erro de digitação corriqueiro, e sem esta recusa ele viraria limite de crédito devolvido a um cliente
 * que não pagou — descoberto só no fechamento, quando ninguém lembra do lançamento.
 *
 * <p>Se o cliente pagou a mais de verdade, o excedente é crédito dele, e crédito não é baixa de pedido:
 * pertence a uma conta corrente de cliente, que esta fatia não tem.
 */
public class PaymentExceedsBalanceException extends RuntimeException {

    private final BigDecimal outstanding;
    private final BigDecimal requested;
    private final String currency;

    public PaymentExceedsBalanceException(BigDecimal outstanding, BigDecimal requested,
            String currency) {
        super("o pedido deve %s %s, e o recebimento lançado é de %s %s"
                .formatted(outstanding.toPlainString(), currency, requested.toPlainString(), currency));
        this.outstanding = outstanding;
        this.requested = requested;
        this.currency = currency;
    }

    public BigDecimal outstanding() {
        return outstanding;
    }

    public BigDecimal requested() {
        return requested;
    }

    public String currency() {
        return currency;
    }
}
