package br.com.brew.brassia.digitaltwin.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * Uma estimativa aprendida do histórico, com a faixa em que se acredita nela (DTW-001).
 *
 * <p><strong>O número sozinho é a parte perigosa desta história.</strong> "Eficiência de 74%" parece um
 * fato e é um resumo: pode vir de trinta brassagens agrupadas em torno de 74, ou de duas — uma de 60 e uma
 * de 88. As duas produzem a mesma média e significam coisas opostas para quem vai planejar a próxima
 * receita. Por isso a média nunca viaja sozinha daqui: ela vem sempre com o tamanho da amostra e a faixa.
 *
 * <p><strong>A faixa é intervalo de confiança da média, não a variação observada.</strong> A distinção
 * importa: a variação diz o quanto as brassagens diferem entre si; o intervalo diz o quanto ainda não se
 * sabe sobre a média delas. Quem planeja precisa do segundo — é ele que encolhe conforme se acumula
 * histórico, e é ele que denuncia que duas brassagens não bastam.
 */
public record Estimate(
        BigDecimal mean,
        BigDecimal standardDeviation,
        BigDecimal lowerBound,
        BigDecimal upperBound,
        int sampleSize,
        Confidence confidence) {

    /**
     * Abaixo disto, não se estima nada.
     *
     * <p>Com uma observação não há desvio a calcular e a "faixa" seria o próprio ponto — uma estimativa
     * com aparência de precisão absoluta construída sobre a menor evidência possível. Devolver
     * {@link #insufficient} é mais honesto que devolver um número.
     */
    public static final int MINIMUM_SAMPLE = 2;

    /** A partir daqui a média para de se mexer muito a cada nova brassagem. */
    private static final int RELIABLE_SAMPLE = 10;

    /** Abaixo disto a estimativa existe, mas não deve guiar decisão sozinha. */
    private static final int LOW_SAMPLE = 5;

    /**
     * Multiplicador do erro padrão para ~95% de confiança.
     *
     * <p>1,96 é o valor da normal. Com amostra pequena o correto seria o t de Student, que é mais largo —
     * e não usá-lo <strong>subestima</strong> a incerteza justamente onde ela é maior. É por isso que
     * amostra pequena não se resolve só com a faixa: ela vem marcada como {@link Confidence#LOW}, e o
     * rótulo é o que impede alguém de ler o intervalo como se fosse preciso. Trocar por t exigiria uma
     * tabela de distribuição, e o rótulo resolve o mesmo problema sem ela.
     */
    private static final BigDecimal Z_95 = new BigDecimal("1.96");

    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);

    public Estimate {
        Objects.requireNonNull(confidence, "confidence");
        // A média é nula exatamente quando não há estimativa, e as duas coisas precisam andar juntas: uma
        // média presente com confiança INSUFFICIENT seria um número que a própria classe diz não valer, e
        // uma média ausente com qualquer outra confiança seria uma estimativa sem estimativa.
        var hasValue = mean != null;
        if (hasValue == (confidence == Confidence.INSUFFICIENT)) {
            throw new IllegalArgumentException(
                    "estimativa incoerente: média " + (hasValue ? "presente" : "ausente")
                            + " com confiança " + confidence);
        }
    }

    /** Não há amostra suficiente para estimar coisa alguma. */
    public static Estimate insufficient(int sampleSize) {
        return new Estimate(null, null, null, null, sampleSize, Confidence.INSUFFICIENT);
    }

    /**
     * Calcula a estimativa a partir das observações.
     *
     * <p>O desvio é amostral ({@code n-1}), não populacional. As brassagens observadas são uma amostra do
     * que a cervejaria produz, não o universo dela — usar {@code n} subestimaria a dispersão, que é o
     * oposto do que uma estimativa honesta deve fazer.
     */
    public static Estimate from(List<BigDecimal> observations) {
        Objects.requireNonNull(observations, "observations");
        var n = observations.size();
        if (n < MINIMUM_SAMPLE) {
            return insufficient(n);
        }

        var count = new BigDecimal(n);
        var sum = observations.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        var mean = sum.divide(count, MC);

        var variance = observations.stream()
                .map(value -> value.subtract(mean).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(new BigDecimal(n - 1), MC);
        var deviation = variance.sqrt(MC);

        // Erro padrão da média: é ele que encolhe com mais observações, e é a raiz de n que faz o retorno
        // ser decrescente — quadruplicar a amostra reduz a incerteza pela metade, não a um quarto.
        var standardError = deviation.divide(count.sqrt(MC), MC);
        var margin = standardError.multiply(Z_95, MC);

        return new Estimate(
                scaled(mean), scaled(deviation),
                scaled(mean.subtract(margin)), scaled(mean.add(margin)),
                n, confidenceFor(n));
    }

    private static Confidence confidenceFor(int n) {
        if (n < MINIMUM_SAMPLE) {
            return Confidence.INSUFFICIENT;
        }
        if (n < LOW_SAMPLE) {
            return Confidence.LOW;
        }
        return n < RELIABLE_SAMPLE ? Confidence.MODERATE : Confidence.HIGH;
    }

    /**
     * Quatro casas.
     *
     * <p>Não é precisão: é o suficiente para densidade (1,0483) e para percentual de eficiência. Guardar
     * dez casas exibiria uma exatidão que a medição de origem não tem — um refratômetro não lê a sétima
     * casa decimal.
     */
    private static BigDecimal scaled(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    public boolean usable() {
        return confidence != Confidence.INSUFFICIENT;
    }

    /** A largura da faixa. É o que responde "quanto ainda não se sabe". */
    public BigDecimal spread() {
        return usable() ? upperBound.subtract(lowerBound) : null;
    }
}
