package br.com.brew.brassia.sales.domain;

import br.com.brew.brassia.shared.money.Money;
import java.math.BigDecimal;

/**
 * O pedido passaria do teto de compromisso em aberto do cliente (SAL-003).
 *
 * <p>Os três números viajam juntos porque um deles sozinho não resolve: saber que "passou do limite" sem
 * saber de quanto é o limite, quanto já está comprometido e quanto este pedido pede deixa quem vende sem
 * ação — e no portal não há um vendedor por perto para explicar.
 */
public class CreditLimitExceededException extends RuntimeException {

    private final BigDecimal ceiling;
    private final BigDecimal committed;
    private final BigDecimal requested;
    private final String currency;

    public CreditLimitExceededException(Money ceiling, Money committed, Money requested) {
        super("o pedido de " + requested + " passa do limite de " + ceiling + ", com " + committed
                + " já comprometido");
        this.ceiling = ceiling.amount();
        this.committed = committed.amount();
        this.requested = requested.amount();
        this.currency = ceiling.currency();
    }

    public BigDecimal ceiling() {
        return ceiling;
    }

    public BigDecimal committed() {
        return committed;
    }

    public BigDecimal requested() {
        return requested;
    }

    public String currency() {
        return currency;
    }
}
