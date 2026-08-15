package br.com.brew.brassia.sales.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Dinheiro com moeda explícita (SAL-001).
 *
 * <p><strong>Existe porque um número solto não é dinheiro.</strong> O critério transversal desta sprint
 * exige "decimal e moeda explícita", e a razão é concreta: uma cervejaria que exporta terá lista em real
 * e lista em dólar, e um {@code BigDecimal} nu permite somar as duas sem que nada reclame. O erro não
 * aparece no dia em que acontece — aparece no fechamento do mês, quando o total não bate e ninguém sabe
 * de onde veio.
 *
 * <p><strong>Não converte.</strong> Conversão exige taxa, data e fonte, e inventar qualquer uma das três
 * seria inventar dinheiro. Operação entre moedas diferentes é recusada; quem precisar converter precisa
 * primeiro decidir com que taxa, e essa decisão não é do domínio de preço.
 *
 * <p><strong>Quatro casas, e não duas.</strong> Preço unitário de item barato — uma tampa, um rótulo —
 * some inteiro num arredondamento para centavo. O arredondamento para dinheiro de verdade acontece no
 * total do pedido, que é assunto da SAL-002.
 */
public record Money(BigDecimal amount, String currency) implements Comparable<Money> {

    private static final int SCALE = 4;

    public Money {
        Objects.requireNonNull(amount, "valor");
        if (currency == null || currency.length() != 3 || !currency.equals(currency.toUpperCase())) {
            // ISO 4217 tem três letras maiúsculas. Aceitar "R$" ou "reais" faria a comparação entre
            // moedas depender de como cada um digitou, e duas listas "iguais" deixariam de ser iguais.
            throw new IllegalArgumentException("a moeda deve ser um código ISO de três letras maiúsculas");
        }
        amount = amount.setScale(SCALE, java.math.RoundingMode.HALF_UP);
    }

    public static Money of(String amount, String currency) {
        return new Money(new BigDecimal(amount), currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money times(int quantity) {
        return new Money(amount.multiply(BigDecimal.valueOf(quantity)), currency);
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount);
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "valor");
        if (!currency.equals(other.currency)) {
            throw new CurrencyMismatchException(currency, other.currency);
        }
    }

    /** Como se escreve num relatório: {@code 12.5000 BRL}. O código vem junto, sempre. */
    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency;
    }
}
