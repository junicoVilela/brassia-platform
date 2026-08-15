package br.com.brew.brassia.forecast.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Quanto se espera vender de um produto, e o quanto disso dá para acreditar (FCST-001).
 *
 * <p><strong>O aceite da sprint pede quatro coisas juntas — "dados, versão, erro e confiança" — e a
 * razão de serem quatro é que o número sozinho mente.</strong> "Vamos vender 400 latas em março" parece
 * um fato e é um resumo: pode vir de doze meses estáveis ou de dois meses que por acaso deram parecido.
 * As duas produzem a mesma média e significam coisas opostas para quem vai decidir uma brassa.
 *
 * <p><strong>Sem histórico suficiente não há previsão.</strong> {@link ForecastConfidence#INSUFFICIENT}
 * não é um número baixo, é a ausência dele — e devolver um número aqui seria o pior resultado possível,
 * porque ele viraria plano de produção e a cerveja que não vender vai vencer na prateleira.
 *
 * <p><strong>O erro vem de backtest, e não de fórmula.</strong> Guardar os últimos meses de fora, prever
 * e comparar com o que de fato aconteceu é a única forma de dizer o quanto o método erra <em>neste</em>
 * produto. Um intervalo calculado só da variância descreveria a dispersão do passado, não a qualidade da
 * previsão.
 *
 * @param sampleMonths quantos meses de histórico entraram — o "dados" do aceite
 * @param method       nome e versão, para duas previsões do mesmo produto serem comparáveis
 * @param meanAbsolutePercentageError erro medido no backtest, em pontos percentuais; vazio quando não
 *                                    houve histórico suficiente para separar treino e teste
 */
public record DemandForecast(UUID productId, YearMonth forMonth, BigDecimal expectedUnits,
        BigDecimal lowerBound, BigDecimal upperBound, int sampleMonths, ForecastMethod method,
        BigDecimal meanAbsolutePercentageError, ForecastConfidence confidence) {

    /**
     * Abaixo disto não se prevê nada.
     *
     * <p>Com dois meses a "média" é a metade da soma de duas observações, e a faixa seria construída
     * sobre a menor evidência que existe. Três é pouco e já permite ver se há tendência; menos que isso
     * é chute com aparência de cálculo.
     */
    public static final int MINIMUM_MONTHS = 3;

    /** A partir de um ciclo anual a sazonalidade aparece no dado, em vez de ser adivinhada. */
    private static final int SEASONAL_MONTHS = 12;

    /** Abaixo disto a previsão existe, mas não deve virar ordem de produção sozinha. */
    private static final int LOW_MONTHS = 6;

    /** Quantos meses ficam de fora do treino para medir o erro. */
    private static final int BACKTEST_MONTHS = 3;

    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);

    public DemandForecast {
        Objects.requireNonNull(productId, "produto");
        Objects.requireNonNull(forMonth, "mês previsto");
        Objects.requireNonNull(method, "método");
        Objects.requireNonNull(confidence, "confiança");
    }

    /** A ausência de previsão, com o motivo legível: histórico curto demais. */
    public static DemandForecast insufficient(UUID productId, YearMonth forMonth, int sampleMonths,
            ForecastMethod method) {
        return new DemandForecast(productId, forMonth, null, null, null, sampleMonths, method, null,
                ForecastConfidence.INSUFFICIENT);
    }

    /**
     * Calcula a previsão a partir do histórico mensal, do mais antigo para o mais recente.
     *
     * <p>A média móvel usa a janela inteira: com o histórico de uma cervejaria pequena, encurtá-la faria
     * a previsão perseguir o último mês, e um mês atípico viraria plano.
     */
    public static DemandForecast from(UUID productId, YearMonth forMonth, List<BigDecimal> monthlyUnits,
            ForecastMethod method) {
        Objects.requireNonNull(monthlyUnits, "histórico");
        var n = monthlyUnits.size();
        if (n < MINIMUM_MONTHS) {
            return insufficient(productId, forMonth, n, method);
        }

        var media = mean(monthlyUnits);
        var desvio = standardDeviation(monthlyUnits, media);
        // A faixa é o intervalo em torno da média, e não a variação observada: ela diz o quanto ainda
        // não se sabe sobre a média, e é ela que encolhe conforme o histórico cresce.
        var erroPadrao = desvio.divide(BigDecimal.valueOf(Math.sqrt(n)), MC);
        var margem = erroPadrao.multiply(BigDecimal.valueOf(1.96), MC);

        return new DemandForecast(productId, forMonth, scale(media),
                scale(media.subtract(margem).max(BigDecimal.ZERO)), scale(media.add(margem)), n, method,
                backtestError(monthlyUnits), confidenceFor(n));
    }

    /**
     * Erro médio absoluto percentual, medido guardando os últimos meses de fora.
     *
     * <p>Vazio quando não há histórico para separar treino e teste — e vazio é resposta honesta: sem
     * backtest não se sabe o quanto o método erra, e um zero ali diria o contrário.
     */
    private static BigDecimal backtestError(List<BigDecimal> monthlyUnits) {
        var n = monthlyUnits.size();
        if (n < MINIMUM_MONTHS + BACKTEST_MONTHS) {
            return null;
        }
        var soma = BigDecimal.ZERO;
        var contados = 0;
        for (var i = n - BACKTEST_MONTHS; i < n; i++) {
            var treino = monthlyUnits.subList(0, i);
            var previsto = mean(treino);
            var real = monthlyUnits.get(i);
            if (real.signum() == 0) {
                // Mês sem venda tornaria o percentual infinito. Pular é melhor que inventar um teto,
                // que faria o erro parecer melhor ou pior conforme o teto escolhido.
                continue;
            }
            var erro = previsto.subtract(real).abs().divide(real, MC)
                    .multiply(BigDecimal.valueOf(100), MC);
            soma = soma.add(erro);
            contados++;
        }
        return contados == 0 ? null : scale(soma.divide(BigDecimal.valueOf(contados), MC));
    }

    private static ForecastConfidence confidenceFor(int months) {
        if (months >= SEASONAL_MONTHS) {
            return ForecastConfidence.HIGH;
        }
        return months >= LOW_MONTHS ? ForecastConfidence.MODERATE : ForecastConfidence.LOW;
    }

    private static BigDecimal mean(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), MC);
    }

    private static BigDecimal standardDeviation(List<BigDecimal> values, BigDecimal mean) {
        var soma = values.stream()
                .map(v -> v.subtract(mean).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // Divisor n-1: a amostra é o que a cervejaria vendeu, e não a população de todos os meses
        // possíveis. Com n pequeno a diferença é grande, e é justamente aqui que ela importa.
        var variancia = soma.divide(BigDecimal.valueOf(values.size() - 1L), MC);
        return BigDecimal.valueOf(Math.sqrt(variancia.doubleValue()));
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /** Se há número. Falso quando o histórico não bastou. */
    public boolean hasNumbers() {
        return confidence != ForecastConfidence.INSUFFICIENT;
    }

    public Optional<BigDecimal> error() {
        return Optional.ofNullable(meanAbsolutePercentageError);
    }

    /**
     * O que a previsão <strong>não</strong> autoriza.
     *
     * <p>O critério transversal da sprint é explícito: "previsão não cria OP ou compra sem confirmação".
     * Este método existe para que a regra tenha um lugar, e não fique só num comentário: nenhuma
     * previsão, de nenhuma confiança, dispensa alguém decidir.
     */
    public boolean mayDriveProductionAlone() {
        return false;
    }
}
