package br.com.brew.brassia.sales.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.brew.brassia.shared.money.Money;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentTest {

    private static final UUID CERVEJARIA = UUID.randomUUID();
    private static final UUID PEDIDO = UUID.randomUUID();
    private static final UUID OPERADOR = UUID.randomUUID();
    private static final LocalDate HOJE = LocalDate.parse("2026-08-18");

    private static Payment recebimento(String valor) {
        return Payment.received(UUID.randomUUID(), CERVEJARIA, PEDIDO, Money.of(valor, "BRL"), HOJE,
                "PIX", null, OPERADOR);
    }

    @Test
    void oRecebimentoParcialENormalENaoExcecao() {
        // Metade na entrega e metade em trinta dias é como boa parte do comércio funciona; exigir o valor
        // cheio faria o operador lançar o que não recebeu para o sistema parar de reclamar.
        var p = recebimento("600.00");

        assertThat(p.amount().amount()).isEqualByComparingTo("600.00");
        assertThat(p.isReversal()).isFalse();
        assertThat(p.signedAmount().amount()).isEqualByComparingTo("600.00");
    }

    @Test
    void oEstornoEEventoCompensatorioENaoEdicao() {
        // Um recebimento lançado errado não se apaga: o registro que se reescreve parece original e diz
        // outra coisa. É o mesmo princípio da prova de entrega (LOG-002).
        var original = recebimento("1200.00");

        var estorno = Payment.reversalOf(UUID.randomUUID(), original, "cheque devolvido", OPERADOR,
                HOJE.plusDays(3));

        assertThat(estorno.isReversal()).isTrue();
        assertThat(estorno.reversesPaymentId()).contains(original.id());
        assertThat(estorno.note()).contains("cheque devolvido");
        // O original continua dizendo o que dizia.
        assertThat(original.amount().amount()).isEqualByComparingTo("1200.00");
        assertThat(original.isReversal()).isFalse();
    }

    @Test
    void oEstornoMoveOSaldoParaBaixo() {
        var original = recebimento("1200.00");
        var estorno = Payment.reversalOf(UUID.randomUUID(), original, "cheque devolvido", OPERADOR,
                HOJE);

        assertThat(estorno.signedAmount().amount()).isEqualByComparingTo("-1200.00");
    }

    @Test
    void oEstornoTemOMesmoValorDoOriginal() {
        // Estornar parte seria corrigir valor, e correção se faz estornando inteiro e lançando de novo —
        // senão a soma passa a depender da ordem em que alguém leu as linhas.
        var original = recebimento("1200.00");
        var estorno = Payment.reversalOf(UUID.randomUUID(), original, "valor digitado a mais", OPERADOR,
                HOJE);

        assertThat(estorno.amount().amount()).isEqualByComparingTo("1200.00");
    }

    @Test
    void naoSeEstornaUmEstorno() {
        var original = recebimento("100.00");
        var estorno = Payment.reversalOf(UUID.randomUUID(), original, "engano", OPERADOR, HOJE);

        assertThatThrownBy(() -> Payment.reversalOf(UUID.randomUUID(), estorno, "de novo", OPERADOR,
                HOJE)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recebimento original");
    }

    @Test
    void oEstornoPrecisaDeMotivo() {
        // Sem ele, quem confere seis meses depois não sabe se foi engano de digitação, cheque devolvido
        // ou pedido cancelado — e as três levam a conversas diferentes.
        var original = recebimento("100.00");

        assertThatThrownBy(() -> Payment.reversalOf(UUID.randomUUID(), original, "  ", OPERADOR, HOJE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("motivo");
    }

    @Test
    void oRecebimentoPrecisaDoMeio() {
        // Sem ele a conciliação com o extrato vira adivinhação: "R$ 1.200 no dia 12" existe três vezes
        // num extrato movimentado.
        assertThatThrownBy(() -> Payment.received(UUID.randomUUID(), CERVEJARIA, PEDIDO,
                Money.of("100", "BRL"), HOJE, "  ", null, OPERADOR))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("meio de pagamento");
    }

    @Test
    void valorNaoPositivoEEstornoDisfarcado() {
        // A soma bateria sem que ninguém conseguisse explicar de onde veio.
        assertThatThrownBy(() -> recebimento("0"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("positivo");
        assertThatThrownBy(() -> recebimento("-50"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
