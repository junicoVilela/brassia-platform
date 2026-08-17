package br.com.brew.brassia.shared.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void aMoedaEObrigatoriaEEmCodigoIso() {
        // Aceitar "R$" faria duas listas iguais deixarem de ser iguais, porque a comparação passaria a
        // depender de como cada um digitou.
        assertThatThrownBy(() -> Money.of("10", "R$")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.of("10", "brl")).isInstanceOf(IllegalArgumentException.class);
        assertThat(Money.of("10", "BRL").currency()).isEqualTo("BRL");
    }

    @Test
    void naoSeSomaRealComDolar() {
        // O erro silencioso só apareceria no fechamento do mês, longe da causa.
        assertThatThrownBy(() -> Money.of("10", "BRL").plus(Money.of("10", "USD")))
                .isInstanceOf(CurrencyMismatchException.class)
                .hasMessageContaining("taxa de conversão");
    }

    @Test
    void nemSeComparamMoedasDiferentes() {
        assertThatThrownBy(() -> Money.of("10", "BRL").compareTo(Money.of("9", "USD")))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void guardaQuatroCasasPorqueItemBaratoSomeEmDuas() {
        // Uma tampa a R$ 0,0125 vira zero se o domínio arredondar para centavo aqui. O arredondamento
        // para dinheiro de verdade é no total do pedido.
        assertThat(Money.of("0.0125", "BRL").amount()).isEqualByComparingTo(new BigDecimal("0.0125"));
        assertThat(Money.of("12.5", "BRL").toString()).isEqualTo("12.5000 BRL");
    }

    @Test
    void somaEMultiplicaDentroDaMesmaMoeda() {
        assertThat(Money.of("10.50", "BRL").plus(Money.of("1.50", "BRL")).amount())
                .isEqualByComparingTo(new BigDecimal("12"));
        assertThat(Money.of("2.25", "BRL").times(4).amount()).isEqualByComparingTo(new BigDecimal("9"));
    }
}
