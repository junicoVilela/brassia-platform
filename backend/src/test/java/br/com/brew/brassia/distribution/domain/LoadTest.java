package br.com.brew.brassia.distribution.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LoadTest {

    private static final UUID CERVEJARIA = UUID.randomUUID();
    private static final UUID QUEM_MONTOU = UUID.randomUUID();
    private static final UUID QUEM_CONFERE = UUID.randomUUID();
    private static final UUID MOTORISTA = UUID.randomUUID();
    private static final LocalDate DIA = LocalDate.parse("2026-08-17");
    private static final Instant AGORA = Instant.parse("2026-08-17T07:00:00Z");

    private static Load carga(String capacidade) {
        return Load.plan(UUID.randomUUID(), CERVEJARIA, "CG-001", DIA, new BigDecimal(capacidade),
                QUEM_MONTOU);
    }

    private static LoadStop parada(int sequencia) {
        return LoadStop.create(UUID.randomUUID(), UUID.randomUUID(), "Bar do Bruno", sequencia,
                new DeliveryWindow(AGORA.plus(Duration.ofHours(1)), AGORA.plus(Duration.ofHours(4))));
    }

    private static Load cargaPronta() {
        var c = carga("1000");
        var p = parada(1);
        c.addStop(p);
        c.load(p.id(), UUID.randomUUID(), new BigDecimal("50"));
        c.assign(MOTORISTA, "Placa ABC-1234");
        return c;
    }

    // --- separação de deveres ---

    @Test
    void quemMontouNaoLibera() {
        // A decisão central da história. A conferência serve para encontrar o erro de quem montou, e quem
        // montou relê o próprio trabalho enxergando o que quis colocar, e não o que colocou.
        var c = cargaPronta();

        assertThatThrownBy(() -> c.release(QUEM_MONTOU, AGORA))
                .isInstanceOf(SeparationOfDutiesException.class)
                .hasMessageContaining("encontrar o erro");

        c.release(QUEM_CONFERE, AGORA);
        assertThat(c.status()).isEqualTo(LoadStatus.RELEASED);
        assertThat(c.releasedBy()).contains(QUEM_CONFERE);
        assertThat(c.releasedAt()).contains(AGORA);
    }

    @Test
    void aCargaVaziaNaoSai() {
        // Uma carga vazia liberada vira um caminhão que sai por engano, e a rota do dia some.
        var c = carga("1000");
        c.addStop(parada(1));
        c.assign(MOTORISTA, "ABC-1234");

        assertThatThrownBy(() -> c.release(QUEM_CONFERE, AGORA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sem nada dentro");
    }

    @Test
    void aCargaNaoSaiSemResponsavel() {
        // Uma carga na rua sem nome é uma carga sem a quem perguntar quando o cliente liga dizendo que
        // não chegou.
        var c = carga("1000");
        var p = parada(1);
        c.addStop(p);
        c.load(p.id(), UUID.randomUUID(), new BigDecimal("50"));

        assertThatThrownBy(() -> c.release(QUEM_CONFERE, AGORA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("responsável");
    }

    // --- congelamento ---

    @Test
    void aCargaLiberadaCongela() {
        // Acrescentar um keg numa carga já conferida desfaz a conferência sem ninguém perceber — e o
        // papel que o motorista leva deixa de descrever o que está no caminhão.
        var c = cargaPronta();
        var outraParada = parada(2);
        c.release(QUEM_CONFERE, AGORA);

        assertThat(c.isFrozen()).isTrue();
        assertThatThrownBy(() -> c.addStop(outraParada))
                .isInstanceOf(IllegalLoadTransitionException.class);
        assertThatThrownBy(() -> c.load(c.route().get(0).id(), UUID.randomUUID(), BigDecimal.ONE))
                .isInstanceOf(IllegalLoadTransitionException.class);
        assertThatThrownBy(() -> c.assign(UUID.randomUUID(), "outra"))
                .isInstanceOf(IllegalLoadTransitionException.class);
    }

    @Test
    void reabrirDerrubaAConferencia() {
        // Manter a conferência de pé depois de a carga mudar seria pior que não ter conferência: o papel
        // diria que alguém olhou aquilo, e ninguém olhou.
        var c = cargaPronta();
        c.release(QUEM_CONFERE, AGORA);
        c.reopen();

        assertThat(c.status()).isEqualTo(LoadStatus.PLANNED);
        assertThat(c.releasedBy()).isEmpty();
        assertThat(c.releasedAt()).isEmpty();
    }

    // --- capacidade ---

    @Test
    void aCapacidadeEDoVeiculoEDizQuantoPassou() {
        // "Excedeu a capacidade" manda o operador tirar itens no chute até caber.
        var c = carga("100");
        var p = parada(1);
        c.addStop(p);
        c.load(p.id(), UUID.randomUUID(), new BigDecimal("50"));
        c.load(p.id(), UUID.randomUUID(), new BigDecimal("30"));

        assertThat(c.loadedLiters()).isEqualByComparingTo("80");
        assertThat(c.remainingLiters()).isEqualByComparingTo("20");

        assertThatThrownBy(() -> c.load(p.id(), UUID.randomUUID(), new BigDecimal("50")))
                .isInstanceOf(LoadCapacityExceededException.class)
                .hasMessageContaining("30");
    }

    @Test
    void aCapacidadeContaAsParadasJuntas() {
        // O caminhão é um só: limitar por parada deixaria passar uma carga que não cabe.
        var c = carga("100");
        var primeira = parada(1);
        var segunda = parada(2);
        c.addStop(primeira);
        c.addStop(segunda);
        c.load(primeira.id(), UUID.randomUUID(), new BigDecimal("60"));

        assertThatThrownBy(() -> c.load(segunda.id(), UUID.randomUUID(), new BigDecimal("50")))
                .isInstanceOf(LoadCapacityExceededException.class);
    }

    // --- roteiro ---

    @Test
    void aSequenciaNaoSeRepete() {
        // Duas paradas na mesma posição é ambiguidade que o motorista resolve inventando — e a rota que
        // ele inventar não é a que a janela combinada pressupõe.
        var c = carga("1000");
        c.addStop(parada(1));

        assertThatThrownBy(() -> c.addStop(parada(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("posição 1");
    }

    @Test
    void oRoteiroSaiNaOrdemDaSequenciaENaoNaDeDigitacao() {
        var c = carga("1000");
        c.addStop(parada(3));
        c.addStop(parada(1));
        c.addStop(parada(2));

        assertThat(c.route()).extracting(LoadStop::sequence).containsExactly(1, 2, 3);
        assertThat(c.nextSequence()).isEqualTo(4);
    }

    @Test
    void oMesmoVasilhameNaoVaiEmDuasParadas() {
        // Entrega prometida duas vezes, e uma delas vai faltar.
        var c = carga("1000");
        var primeira = parada(1);
        var segunda = parada(2);
        c.addStop(primeira);
        c.addStop(segunda);
        var keg = UUID.randomUUID();
        c.load(primeira.id(), keg, new BigDecimal("50"));

        assertThatThrownBy(() -> c.load(segunda.id(), keg, new BigDecimal("50")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("já está nesta carga");
    }

    @Test
    void tirarUmVasilhameLiberaACapacidade() {
        var c = carga("100");
        var p = parada(1);
        c.addStop(p);
        var keg = UUID.randomUUID();
        c.load(p.id(), keg, new BigDecimal("50"));
        c.unload(keg);

        assertThat(c.loadedLiters()).isEqualByComparingTo("0");
        assertThat(c.containsContainer(keg)).isFalse();
    }

    // --- janela ---

    @Test
    void aJanelaNaoFechaAntesDeAbrir() {
        // Engano de digitação que o motorista só descobriria com o caminhão carregado.
        assertThatThrownBy(() -> new DeliveryWindow(AGORA, AGORA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("terminar depois");
    }

    @Test
    void aJanelaSabeDizerSeAEntregaCaiuFora() {
        // O que a LOG-002 precisa para explicar a ocorrência, e não só registrá-la.
        var janela = new DeliveryWindow(AGORA, AGORA.plus(Duration.ofHours(3)));

        assertThat(janela.contains(AGORA.plus(Duration.ofHours(1)))).isTrue();
        assertThat(janela.missedAt(AGORA.plus(Duration.ofHours(4)))).isTrue();
    }

    @Test
    void aParadaSemJanelaEEstadoLegitimo() {
        // Nem toda entrega tem hora combinada, e obrigar uma faria alguém inventar "8h às 18h".
        var p = LoadStop.create(UUID.randomUUID(), UUID.randomUUID(), "Bar", 1, null);

        assertThat(p.window()).isEmpty();
    }

    // --- ciclo ---

    @Test
    void oCicloNaoPulaEtapas() {
        var c = cargaPronta();

        assertThatThrownBy(c::depart).isInstanceOf(IllegalLoadTransitionException.class);
        c.release(QUEM_CONFERE, AGORA);
        assertThatThrownBy(c::close).isInstanceOf(IllegalLoadTransitionException.class);
        c.depart();
        c.close();

        assertThat(c.status()).isEqualTo(LoadStatus.CLOSED);
        assertThatThrownBy(c::cancel).isInstanceOf(IllegalLoadTransitionException.class);
    }

    @Test
    void aCargaContaClientesDistintos() {
        // Oito paradas em três clientes é uma rota diferente de oito paradas em oito.
        var c = carga("1000");
        var cliente = UUID.randomUUID();
        c.addStop(LoadStop.create(UUID.randomUUID(), cliente, "Bar", 1, null));
        c.addStop(LoadStop.create(UUID.randomUUID(), cliente, "Bar — depósito", 2, null));
        c.addStop(LoadStop.create(UUID.randomUUID(), UUID.randomUUID(), "Outro", 3, null));

        assertThat(c.route()).hasSize(3);
        assertThat(c.customerCount()).isEqualTo(2);
    }
}
