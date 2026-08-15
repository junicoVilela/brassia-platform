package br.com.brew.brassia.forecast.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class DemandForecastTest {

    private static final UUID PRODUTO = UUID.randomUUID();
    private static final YearMonth MARCO = YearMonth.of(2026, 3);
    private static final ForecastMethod METODO = ForecastMethod.MOVING_AVERAGE_V1;

    private static List<BigDecimal> meses(String... valores) {
        return Stream.of(valores).map(BigDecimal::new).toList();
    }

    private static DemandForecast prever(List<BigDecimal> historico) {
        return DemandForecast.from(PRODUTO, MARCO, historico, METODO);
    }

    @Test
    void semHistoricoSuficienteNaoHaPrevisao() {
        // INSUFFICIENT não é um número baixo, é a ausência dele. Devolver um número aqui seria o pior
        // resultado: ele viraria plano de produção, e a cerveja que não vender vence na prateleira.
        var f = prever(meses("100", "120"));

        assertThat(f.confidence()).isEqualTo(ForecastConfidence.INSUFFICIENT);
        assertThat(f.hasNumbers()).isFalse();
        assertThat(f.expectedUnits()).isNull();
        assertThat(f.lowerBound()).isNull();
        assertThat(f.upperBound()).isNull();
        // O tamanho da amostra continua sendo dito: é o que explica a recusa.
        assertThat(f.sampleMonths()).isEqualTo(2);
    }

    @Test
    void comTresMesesJaHaPrevisaoMasDeConfiancaBaixa() {
        // Três é pouco e já permite ver tendência; a confiança diz que não deve virar OP sozinha.
        var f = prever(meses("100", "110", "120"));

        assertThat(f.hasNumbers()).isTrue();
        assertThat(f.confidence()).isEqualTo(ForecastConfidence.LOW);
        assertThat(f.expectedUnits()).isEqualByComparingTo("110.00");
    }

    @Test
    void aConfiancaSobeComOHistorico() {
        assertThat(prever(meses("100", "100", "100", "100", "100", "100")).confidence())
                .isEqualTo(ForecastConfidence.MODERATE);

        // Doze meses: só a partir de um ciclo anual a sazonalidade aparece no dado.
        var ano = meses("100", "100", "100", "100", "100", "100",
                "100", "100", "100", "100", "100", "100");
        assertThat(prever(ano).confidence()).isEqualTo(ForecastConfidence.HIGH);
    }

    @Test
    void aFaixaEncolheQuandoOHistoricoConcorda() {
        // Meses parecidos dão faixa estreita; meses díspares dão faixa larga. É a faixa que denuncia
        // que a média esconde coisas diferentes.
        var estavel = prever(meses("100", "100", "100", "100", "100", "100"));
        var instavel = prever(meses("20", "180", "40", "160", "60", "140"));

        assertThat(estavel.expectedUnits()).isEqualByComparingTo(instavel.expectedUnits());
        var larguraEstavel = estavel.upperBound().subtract(estavel.lowerBound());
        var larguraInstavel = instavel.upperBound().subtract(instavel.lowerBound());
        assertThat(larguraEstavel).isLessThan(larguraInstavel);
    }

    @Test
    void aFaixaNaoDesceAbaixoDeZero() {
        // Demanda negativa não existe, e um limite inferior negativo faria a tela mostrar que se pode
        // vender menos que nada.
        var f = prever(meses("5", "1", "200", "2", "3", "1"));

        assertThat(f.lowerBound()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }

    @Test
    void semHistoricoParaBacktestOErroEVazio() {
        // Vazio é honesto: sem separar treino e teste não se sabe o quanto o método erra, e um zero ali
        // diria o contrário.
        var f = prever(meses("100", "110", "120"));

        assertThat(f.error()).isEmpty();
    }

    @Test
    void comHistoricoSuficienteOErroEMedidoNoBacktest() {
        // Seis meses: três de treino e três guardados para comparar com o que de fato aconteceu.
        var f = prever(meses("100", "100", "100", "100", "100", "100"));

        // Histórico perfeitamente estável: o método acerta, e o erro é zero.
        assertThat(f.error()).isPresent();
        assertThat(f.error().orElseThrow()).isEqualByComparingTo("0.00");
    }

    @Test
    void oErroCresceQuandoOMetodoErra() {
        // Demanda em rampa: a média móvel fica sempre atrás, e o backtest mostra isso.
        var f = prever(meses("10", "20", "30", "40", "50", "60"));

        assertThat(f.error().orElseThrow()).isGreaterThan(new BigDecimal("10"));
    }

    @Test
    void mesSemVendaNaoInflaOErro() {
        // Percentual sobre zero seria infinito. Pular é melhor que inventar um teto, que faria o erro
        // parecer melhor ou pior conforme o teto escolhido.
        var f = prever(meses("100", "100", "100", "0", "0", "0"));

        assertThat(f.error()).isEmpty();
    }

    @Test
    void aVersaoDoMetodoViajaComOResultado() {
        // Sem ela, duas previsões do mesmo produto em meses diferentes não são comparáveis: ninguém
        // sabe se o número mudou porque a demanda mudou ou porque o método mudou.
        var f = prever(meses("100", "110", "120"));

        assertThat(f.method().label()).isEqualTo("moving-average v1");
    }

    @Test
    void nenhumaPrevisaoDispensaAlguemDecidir() {
        // O critério transversal da sprint é explícito: previsão não cria OP nem compra sem confirmação.
        var confiante = prever(meses("100", "100", "100", "100", "100", "100",
                "100", "100", "100", "100", "100", "100"));

        assertThat(confiante.confidence()).isEqualTo(ForecastConfidence.HIGH);
        assertThat(confiante.mayDriveProductionAlone()).isFalse();
    }
}
