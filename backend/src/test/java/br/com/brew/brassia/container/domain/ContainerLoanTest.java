package br.com.brew.brassia.container.domain;

import br.com.brew.brassia.shared.money.Money;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ContainerLoanTest {

    private static final UUID CERVEJARIA = UUID.randomUUID();
    private static final UUID KEG = UUID.randomUUID();
    private static final UUID CLIENTE = UUID.randomUUID();
    private static final Instant SAIDA = Instant.parse("2026-08-01T10:00:00Z");
    private static final LocalDate PRAZO = LocalDate.parse("2026-08-31");

    private static ContainerLoan emprestimo(Money caucao) {
        return ContainerLoan.open(UUID.randomUUID(), CERVEJARIA, KEG, CLIENTE, "Bar do Bruno", SAIDA,
                PRAZO, caucao);
    }

    // --- prazo ---

    @Test
    void atrasadoEOQueNaoVoltouDepoisDoPrazo() {
        // "No cliente há dois dias" e "no cliente há sete meses" precisam ser linhas diferentes na tela.
        var e = emprestimo(null);

        assertThat(e.overdueOn(PRAZO)).isFalse();
        assertThat(e.overdueOn(PRAZO.plusDays(1))).isTrue();
        assertThat(e.daysLate(PRAZO.plusDays(5))).isEqualTo(5);
    }

    @Test
    void oQueJaVoltouNaoEstaAtrasado() {
        // Atraso é dívida em aberto; devolvido tarde é histórico, e as duas listas servem a decisões
        // diferentes.
        var e = emprestimo(null);
        e.returned(Instant.parse("2026-09-03T09:00:00Z"));

        assertThat(e.overdueOn(PRAZO.plusDays(10))).isFalse();
        assertThat(e.daysLate(PRAZO.plusDays(10))).isZero();
        assertThat(e.returnedLate()).isTrue();
    }

    @Test
    void oAtrasoNuncaENegativo() {
        // "Faltam três dias" é outra pergunta: devolvê-la aqui faria alguém somar atrasos com folgas e
        // chegar a zero sem nenhum keg no lugar.
        assertThat(emprestimo(null).daysLate(PRAZO.minusDays(3))).isZero();
    }

    @Test
    void oPrazoNaoNasceVencido() {
        // Um prazo anterior à saída viraria cobrança errada, e o operador só descobriria depois.
        assertThatThrownBy(() -> ContainerLoan.open(UUID.randomUUID(), CERVEJARIA, KEG, CLIENTE,
                "Bar", SAIDA, LocalDate.parse("2026-07-31"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("anterior à saída");
    }

    // --- caução ---

    @Test
    void aCaucaoTemMoedaExplicita() {
        // Um número solto não é dinheiro: a casa que exporta terá caução em real e em dólar. É o mesmo
        // `Money` de vendas — promovido a `shared` quando esta história precisou da mesma regra
        // (DEB-CON-002), em vez de duas definições que podem divergir em silêncio.
        var c = Money.of("120.00", "BRL");

        assertThat(c.amount()).isEqualByComparingTo("120.00");
        assertThat(c.currency()).isEqualTo("BRL");
        assertThat(c.toMinorUnit().scale()).isEqualTo(2);
        assertThatThrownBy(() -> Money.of("120", "R$"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ISO");
    }

    @Test
    void caucaoZeroNaoECaucao() {
        // A ausência de caução se representa com nulo, e não com zero: zero somaria no relatório de
        // valores retidos como se houvesse dinheiro parado.
        //
        // A regra vive no EMPRÉSTIMO, e não no tipo de dinheiro: zero é valor legítimo para um total de
        // pedido, e proibi-lo no `Money` quebraria vendas para consertar contêineres.
        assertThatThrownBy(() -> emprestimo(Money.of("0", "BRL")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("positiva");
    }

    @Test
    void oEmprestimoSemCaucaoEEstadoLegitimo() {
        // Nem toda casa cobra caução, e obrigar um valor faria alguém digitar 1 real para poder seguir.
        var e = emprestimo(null);

        assertThat(e.hasDeposit()).isFalse();
        assertThat(e.depositOutcome()).isEqualTo(DepositOutcome.HELD);
    }

    @Test
    void aDevolucaoTornaACaucaoDevidaEAPerdaARetem() {
        // Isto é a DECISÃO, e não o dinheiro: devolver a caução é lançamento financeiro, e fingir que ele
        // acontece aqui faria o sistema afirmar um pagamento que ninguém fez.
        var devolvido = emprestimo(Money.of("120.00", "BRL"));
        devolvido.returned(Instant.parse("2026-08-20T10:00:00Z"));
        assertThat(devolvido.depositOutcome()).isEqualTo(DepositOutcome.TO_REFUND);

        var perdido = emprestimo(Money.of("120.00", "BRL"));
        perdido.lost(Instant.parse("2026-10-01T10:00:00Z"), "o bar fechou e não devolveu");
        assertThat(perdido.depositOutcome()).isEqualTo(DepositOutcome.RETAINED);
    }

    // --- perda ---

    @Test
    void aPerdaPrecisaDeMotivo() {
        // "Perdido" sozinho não distingue o bar que fechou do keg roubado do caminhão — e as duas coisas
        // terminam em conversas diferentes com o cliente.
        var e = emprestimo(null);

        assertThatThrownBy(() -> e.lost(Instant.now(), "  "))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("motivo");
    }

    @Test
    void oEmprestimoNaoSeEncerraDuasVezes() {
        // Encerrar de novo reescreveria a data que a cobrança usou.
        var e = emprestimo(null);
        e.returned(Instant.parse("2026-08-20T10:00:00Z"));

        assertThatThrownBy(() -> e.lost(Instant.now(), "sumiu"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> e.returned(Instant.now()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aDevolucaoNaoEAnteriorASaida() {
        var e = emprestimo(null);

        assertThatThrownBy(() -> e.returned(SAIDA.minus(Duration.ofDays(1))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("anterior à saída");
    }

    @Test
    void oNomeDoClienteECongelado() {
        // Renomear o cliente não reescreve o comprovante de caução que ele tem na mão.
        assertThat(emprestimo(null).customerName()).isEqualTo("Bar do Bruno");
        assertThatThrownBy(() -> ContainerLoan.open(UUID.randomUUID(), CERVEJARIA, KEG, CLIENTE, " ",
                SAIDA, PRAZO, null)).isInstanceOf(IllegalArgumentException.class);
    }

    // --- higienização ---

    @Test
    void aHigienizacaoDizOQueFoiFeito() {
        // "Higienizado" sem dizer como é um carimbo, e um carimbo não se audita. A pergunta real chega
        // três meses depois: aquele keg foi lavado antes da cerveja que o cliente reclamou?
        var h = new SanitationRecord(UUID.randomUUID(), KEG, SAIDA, UUID.randomUUID(),
                "soda 2% a 60 °C", null);

        assertThat(h.method()).isEqualTo("soda 2% a 60 °C");
        assertThatThrownBy(() -> new SanitationRecord(UUID.randomUUID(), KEG, SAIDA,
                UUID.randomUUID(), "   ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("o que foi feito");
    }

    @Test
    void aHigienizacaoTemResponsavel() {
        // Sem nome não há a quem perguntar, e é justamente isso que uma auditoria procura.
        assertThatThrownBy(() -> new SanitationRecord(UUID.randomUUID(), KEG, SAIDA, null,
                "soda 2%", null)).isInstanceOf(NullPointerException.class);
    }
}
