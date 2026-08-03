package br.com.brew.brassia.metrology.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Curva de correção do certificado (MTR-002): os pontos em que o instrumento foi comparado ao
 * padrão.
 *
 * <p>Não é fórmula, é <strong>dado do certificado daquele instrumento</strong> — por isso
 * interpolar aqui, e não no hub `calculator`, que existe para fórmulas compartilhadas e
 * versionadas e cujo contrato aceita apenas entradas escalares.
 *
 * <p>A interpolação é linear entre os dois pontos vizinhos. <strong>Fora da faixa verificada a
 * correção é recusada</strong>: extrapolar produziria um número com aparência de corrigido sobre
 * uma região que ninguém conferiu.
 */
public final class CorrectionCurve {

    private static final MathContext MC = new MathContext(12, RoundingMode.HALF_UP);

    private final List<CurvePoint> points;

    private CorrectionCurve(List<CurvePoint> points) {
        this.points = List.copyOf(points);
    }

    /**
     * @throws IllegalArgumentException se houver menos de dois pontos ou se os valores indicados
     *     não forem estritamente crescentes — curva não monótona torna a correção ambígua, porque
     *     a mesma indicação corresponderia a mais de um valor verdadeiro
     */
    public static CorrectionCurve of(List<CurvePoint> rawPoints) {
        Objects.requireNonNull(rawPoints, "pontos da curva");
        if (rawPoints.size() < 2) {
            throw new IllegalArgumentException("a curva precisa de ao menos dois pontos");
        }
        var sorted = rawPoints.stream()
                .sorted(Comparator.comparing(CurvePoint::measured))
                .toList();
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).measured().compareTo(sorted.get(i - 1).measured()) == 0) {
                throw new IllegalArgumentException("a curva tem duas leituras iguais: "
                        + sorted.get(i).measured());
            }
            if (sorted.get(i).reference().compareTo(sorted.get(i - 1).reference()) <= 0) {
                throw new IllegalArgumentException(
                        "a curva não é monótona: leitura maior deveria corresponder a referência maior");
            }
        }
        return new CorrectionCurve(sorted);
    }

    /** Valor verdadeiro correspondente à indicação {@code measured}, por interpolação linear. */
    public BigDecimal correct(BigDecimal measured) {
        Objects.requireNonNull(measured, "leitura");
        var min = min();
        var max = max();
        if (measured.compareTo(min) < 0 || measured.compareTo(max) > 0) {
            throw new OutsideCurveRangeException(measured, min, max);
        }
        for (int i = 1; i < points.size(); i++) {
            var low = points.get(i - 1);
            var high = points.get(i);
            if (measured.compareTo(high.measured()) <= 0) {
                var span = high.measured().subtract(low.measured());
                var ratio = measured.subtract(low.measured()).divide(span, MC);
                return low.reference()
                        .add(high.reference().subtract(low.reference()).multiply(ratio, MC), MC);
            }
        }
        // Inalcançável: `measured` já foi validado contra o máximo.
        throw new IllegalStateException("ponto da curva não localizado");
    }

    public BigDecimal min() {
        return points.get(0).measured();
    }

    public BigDecimal max() {
        return points.get(points.size() - 1).measured();
    }

    public List<CurvePoint> points() {
        return points;
    }

    /** Descrição determinística do que foi aplicado, para o resultado dizer de onde veio. */
    public String method() {
        return "interpolação linear entre %d pontos do certificado (%s a %s)"
                .formatted(points.size(), min(), max());
    }
}
