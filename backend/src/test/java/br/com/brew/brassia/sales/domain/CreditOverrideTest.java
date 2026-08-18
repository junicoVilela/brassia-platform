package br.com.brew.brassia.sales.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.brew.brassia.shared.money.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** A autorização de pedido acima do teto de crédito (SAL-004). */
class CreditOverrideTest {

    private static final UUID QUEM = UUID.randomUUID();
    private static final Instant AGORA = Instant.parse("2026-08-18T12:00:00Z");

    @Test
    void aAutorizacaoPrecisaDeMotivo() {
        // "Autorizado" sem motivo é a mesma coisa que não ter teto: ninguém consegue julgar depois se a
        // exceção foi razoável.
        assertThatThrownBy(() -> new CreditOverride("  ", QUEM, AGORA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("motivo");
        assertThatThrownBy(() -> new CreditOverride(null, QUEM, AGORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ehTudoOuNada() {
        // Motivo sem autor, ou autor sem data, é um registro que não responde a pergunta para a qual ele
        // existe: quem deixou passar, quando, e por quê.
        assertThatThrownBy(() -> new CreditOverride("pagamento cai hoje", null, AGORA))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CreditOverride("pagamento cai hoje", QUEM, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void oPedidoNasceSemAutorizacao() {
        // Nulo é o normal: o pedido coube no teto, ou não havia teto.
        assertThat(pedido().creditOverride()).isEmpty();
    }

    @Test
    void naoSeAutorizaDuasVezes() {
        // Reescreveria quem autorizou, e é esse nome que responde a pergunta do dono seis meses depois.
        var order = pedido();
        order.authorizeAboveCredit(new CreditOverride("pagamento cai hoje", QUEM, AGORA));

        assertThatThrownBy(() -> order.authorizeAboveCredit(
                new CreditOverride("outro motivo", UUID.randomUUID(), AGORA)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(order.creditOverride().orElseThrow().reason()).isEqualTo("pagamento cai hoje");
    }

    private static SalesOrder pedido() {
        // A reserva é obrigatória: um pedido que não reservou nada não segura lote (SAL-002).
        var reserva = new LotReservation(UUID.randomUUID(), "LOTE-100/1", 10,
                LocalDate.of(2027, 1, 10));
        var linha = new OrderLine(UUID.randomUUID(), "IPA-473", 10, Money.of("12", "BRL"), false,
                List.of(reserva));
        return SalesOrder.place(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "PED-1", List.of(linha), LocalDate.of(2026, 8, 18), null, AGORA);
    }
}
