package br.com.brew.brassia.metrology.domain;

import java.math.BigDecimal;

/**
 * A leitura caiu fora da faixa que o certificado verificou.
 *
 * <p>Extrapolar a curva inventaria exatidão que o certificado não sustenta: fora dos pontos
 * conferidos ninguém sabe como o instrumento se comporta, e um número extrapolado sairia com a
 * mesma cara de um número medido.
 */
public final class OutsideCurveRangeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final BigDecimal value;
    private final BigDecimal curveMin;
    private final BigDecimal curveMax;

    public OutsideCurveRangeException(BigDecimal value, BigDecimal curveMin, BigDecimal curveMax) {
        super("leitura %s está fora da faixa verificada pelo certificado (%s a %s)"
                .formatted(value, curveMin, curveMax));
        this.value = value;
        this.curveMin = curveMin;
        this.curveMax = curveMax;
    }

    public BigDecimal value() {
        return value;
    }

    public BigDecimal curveMin() {
        return curveMin;
    }

    public BigDecimal curveMax() {
        return curveMax;
    }
}
