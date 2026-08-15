package br.com.brew.brassia.sales.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PriceScheduleTest {

    private static final UUID PRODUTO = UUID.randomUUID();
    private static final UUID CANAL = UUID.randomUUID();
    private static final LocalDate JANEIRO = LocalDate.parse("2026-01-01");
    private static final LocalDate MARCO = LocalDate.parse("2026-03-01");

    private static PriceSchedule vazia() {
        return PriceSchedule.empty(PRODUTO, CANAL);
    }

    @Test
    void semPrecoAConsultaEVaziaENaoZero() {
        // Zero faria uma venda sair de graça. "Não havia preço" é resposta legítima.
        assertThat(vazia().priceOn(JANEIRO)).isEmpty();
    }

    @Test
    void oPrecoNaoValeAntesDeComecar() {
        var p = vazia();
        p.priceFrom(Money.of("12", "BRL"), false, MARCO);

        assertThat(p.priceOn(JANEIRO)).isEmpty();
        assertThat(p.priceOn(MARCO)).isPresent();
    }

    @Test
    void oPrecoNovoFechaOAnteriorNaVespera() {
        // O ato comum não é "inserir vigência", é "a partir de tal dia passa a custar tanto". Exigir que
        // o operador feche o antigo à mão criaria uma janela sem preço num dia de venda.
        var p = vazia();
        p.priceFrom(Money.of("12", "BRL"), false, JANEIRO);
        var mudanca = p.priceFrom(Money.of("14", "BRL"), false, MARCO);

        assertThat(mudanca.closedEntry()).isPresent();
        assertThat(mudanca.closedEntry().orElseThrow().validTo()).isEqualTo(LocalDate.parse("2026-02-28"));

        // Em qualquer dia, um preço só.
        assertThat(p.priceOn(LocalDate.parse("2026-02-28")).orElseThrow().price().amount())
                .isEqualByComparingTo("12");
        assertThat(p.priceOn(MARCO).orElseThrow().price().amount()).isEqualByComparingTo("14");
    }

    @Test
    void oUltimoDiaDaVigenciaAindaValeOPrecoAntigo() {
        // As duas pontas são inclusivas: quem compra no último dia paga o preço daquele dia.
        var p = vazia();
        p.priceFrom(Money.of("12", "BRL"), false, JANEIRO);
        p.priceFrom(Money.of("14", "BRL"), false, MARCO);

        assertThat(p.priceOn(LocalDate.parse("2026-02-28")).orElseThrow().price().amount())
                .isEqualByComparingTo("12");
    }

    @Test
    void naoSeSobrepoePeriodoJaFechado() {
        // Encurtar o antigo, dividir em dois, substituir? Adivinhar seria reescrever preço histórico.
        var p = vazia();
        p.priceFrom(Money.of("12", "BRL"), false, JANEIRO);
        p.priceFrom(Money.of("14", "BRL"), false, MARCO);

        assertThatThrownBy(() -> p.priceFrom(Money.of("13", "BRL"), false, LocalDate.parse("2026-02-01")))
                .isInstanceOf(OverlappingPriceException.class);
    }

    @Test
    void naoSeAbreDoisPrecosNoMesmoDia() {
        // Fechar o de hoje na véspera criaria uma vigência que termina antes de começar. É o operador
        // corrigindo o que acabou de cadastrar, e a correção certa é apagar o errado.
        var p = vazia();
        p.priceFrom(Money.of("12", "BRL"), false, MARCO);

        assertThatThrownBy(() -> p.priceFrom(Money.of("13", "BRL"), false, MARCO))
                .isInstanceOf(OverlappingPriceException.class);
    }

    @Test
    void aLinhaDoTempoNaoTrocaDeMoeda() {
        // "Aumentou ou baixou?" deixaria de ter resposta se a moeda mudasse no meio.
        var p = vazia();
        p.priceFrom(Money.of("12", "BRL"), false, JANEIRO);

        assertThatThrownBy(() -> p.priceFrom(Money.of("3", "USD"), false, MARCO))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void oPrecoZeroERecusado() {
        // Brinde é desconto no pedido, onde fica registrado que alguém decidiu dar. Aqui, zero é engano.
        assertThatThrownBy(() -> vazia().priceFrom(Money.of("0", "BRL"), false, JANEIRO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positivo");
    }

    @Test
    void aVigenciaNaoTerminaAntesDeComecar() {
        assertThatThrownBy(() -> new PriceEntry(Money.of("10", "BRL"), false, MARCO, JANEIRO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("antes de começar");
    }

    @Test
    void aMarcaDeImpostoAcompanhaOPreco() {
        // A plataforma não calcula imposto (fora do escopo da sprint), mas precisa saber se o número já
        // o contém — senão alguém compara preço com imposto contra preço sem, e conclui errado.
        var p = vazia();
        p.priceFrom(Money.of("12", "BRL"), true, JANEIRO);

        assertThat(p.priceOn(JANEIRO).orElseThrow().taxIncluded()).isTrue();
    }

    @Test
    void aLinhaDoTempoSaiEmOrdemENaoSeAlteraPorFora() {
        var p = vazia();
        p.priceFrom(Money.of("12", "BRL"), false, JANEIRO);
        p.priceFrom(Money.of("14", "BRL"), false, MARCO);

        assertThat(p.entries()).hasSize(2);
        assertThat(p.entries().getFirst().validFrom()).isEqualTo(JANEIRO);
        assertThatThrownBy(() -> p.entries().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
