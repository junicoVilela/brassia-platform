package br.com.brew.brassia.container.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * A caução do vasilhame, com moeda explícita.
 *
 * <p><strong>Por que não é o {@code Money} de vendas.</strong> Aquele vive dentro do domínio de
 * {@code sales} — não é porta publicada —, e importá-lo daqui furaria a fronteira do módulo para
 * economizar vinte linhas. Copiar a regra é o preço normal de manter os módulos separados; promover
 * {@code Money} a tipo compartilhado é decisão que merece uma história própria (DEB-CON-002).
 *
 * <p>Duas casas, e não quatro: caução é valor cobrado do cliente, e ele existe em centavos desde o
 * primeiro dia — diferente de preço unitário, que se compõe.
 */
public record DepositAmount(BigDecimal amount, String currency) {

    public DepositAmount {
        Objects.requireNonNull(amount, "valor");
        if (amount.signum() <= 0) {
            // Caução zero não é caução: é a ausência dela, e a ausência se representa com nulo.
            throw new IllegalArgumentException("a caução deve ser positiva");
        }
        if (currency == null || currency.length() != 3 || !currency.equals(currency.toUpperCase())) {
            throw new IllegalArgumentException("a moeda deve ser um código ISO de três letras maiúsculas");
        }
        amount = amount.setScale(2, RoundingMode.HALF_UP);
    }

    public static DepositAmount of(String amount, String currency) {
        return new DepositAmount(new BigDecimal(amount), currency);
    }
}
