package br.com.brew.brassia.sales.domain;

import br.com.brew.brassia.shared.money.CurrencyMismatchException;
import br.com.brew.brassia.shared.money.Money;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CreditLimitTest {

    private static final Money ZERO = Money.of("0", "BRL");

    @Test
    void semLimiteTudoCabe() {
        // Não recusar por falta de decisão é reversível; recusar um pedido bom porque alguém chutou um
        // teto não é — o cliente vai comprar de outro.
        var sem = CreditLimit.none();

        assertThat(sem.isDefined()).isFalse();
        assertThat(sem.fits(Money.of("1000000", "BRL"), Money.of("999999", "BRL"))).isTrue();
    }

    @Test
    void comLimiteOPedidoCabeAteOTeto() {
        var limite = CreditLimit.of(Money.of("1000", "BRL"));

        assertThat(limite.fits(Money.of("600", "BRL"), Money.of("400", "BRL"))).isTrue();
        assertThat(limite.fits(Money.of("600", "BRL"), Money.of("401", "BRL"))).isFalse();
    }

    @Test
    void oTetoEInclusivo() {
        // Bater exatamente no limite é usar o limite, e não ultrapassá-lo.
        var limite = CreditLimit.of(Money.of("1000", "BRL"));

        assertThat(limite.fits(ZERO, Money.of("1000", "BRL"))).isTrue();
    }

    @Test
    void oTetoZeroERecusado() {
        // Teto zero bloquearia toda compra, o que não é limite de crédito e sim cliente suspenso — e
        // suspender é decisão diferente, que se toma desativando o cliente.
        assertThatThrownBy(() -> CreditLimit.of(Money.of("0", "BRL")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positivo");
    }

    @Test
    void naoSeComparaCreditoEmMoedasDiferentes() {
        // Uma decisão de crédito baseada em real somado com dólar sem taxa é uma decisão sobre um
        // número que não existe.
        var limite = CreditLimit.of(Money.of("1000", "BRL"));

        assertThatThrownBy(() -> limite.fits(ZERO, Money.of("100", "USD")))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void oQueEleMedeERecebivel() {
        // O agregado só compara dois números; quem os produz é a consulta do recebível (DEB-SAL-002):
        // pedidos confirmados e atendidos, menos os recebimentos. O que este teste fixa é que o teto é
        // comparado com a soma, e não com o pedido isolado. Ver PaymentIT.
        var limite = CreditLimit.of(Money.of("1000", "BRL"));

        // Cliente devendo 900: só cabem mais 100.
        assertThat(limite.fits(Money.of("900", "BRL"), Money.of("100", "BRL"))).isTrue();
        assertThat(limite.fits(Money.of("900", "BRL"), Money.of("200", "BRL"))).isFalse();
    }
}
