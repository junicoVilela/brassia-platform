package br.com.brew.brassia.metrology.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Faixa de medição, resolução e precisão do instrumento — as três coisas que dizem o que uma
 * leitura dele significa.
 *
 * <p>Resolução é a menor divisão que o instrumento mostra; precisão é o erro máximo admitido pelo
 * certificado. Um instrumento com resolução maior que a própria amplitude não mede nada, e
 * precisão pior que a amplitude descreve um aparelho cujo erro cobre toda a escala: os dois casos
 * são recusados na construção, porque um cadastro assim envenena qualquer leitura futura.
 *
 * @param unit unidade única da faixa, da resolução e da precisão — comparar °C com °F em silêncio
 *             seria pior que recusar
 */
public record MeasurementRange(BigDecimal min, BigDecimal max, BigDecimal resolution,
        BigDecimal accuracy, String unit) {

    public MeasurementRange {
        Objects.requireNonNull(min, "mínimo da faixa é obrigatório");
        Objects.requireNonNull(max, "máximo da faixa é obrigatório");
        Objects.requireNonNull(resolution, "resolução é obrigatória");
        Objects.requireNonNull(accuracy, "precisão é obrigatória");
        unit = requireUnit(unit);
        if (min.compareTo(max) >= 0) {
            throw new IllegalArgumentException("o mínimo da faixa deve ser menor que o máximo");
        }
        if (resolution.signum() <= 0) {
            throw new IllegalArgumentException("a resolução deve ser positiva");
        }
        if (accuracy.signum() <= 0) {
            throw new IllegalArgumentException("a precisão deve ser positiva");
        }
        var amplitude = max.subtract(min);
        if (resolution.compareTo(amplitude) > 0) {
            throw new IllegalArgumentException("a resolução não pode ser maior que a amplitude da faixa");
        }
        if (accuracy.compareTo(amplitude) > 0) {
            throw new IllegalArgumentException("a precisão não pode ser maior que a amplitude da faixa");
        }
    }

    /** Amplitude da faixa (máximo − mínimo). */
    public BigDecimal amplitude() {
        return max.subtract(min);
    }

    /** Se o valor cai dentro da faixa, extremos inclusive. */
    public boolean covers(BigDecimal value) {
        Objects.requireNonNull(value, "valor");
        return value.compareTo(min) >= 0 && value.compareTo(max) <= 0;
    }

    private static String requireUnit(String unit) {
        if (unit == null || unit.isBlank()) {
            throw new IllegalArgumentException("unidade é obrigatória");
        }
        var trimmed = unit.trim();
        if (trimmed.length() > 20) {
            throw new IllegalArgumentException("unidade excede 20 caracteres");
        }
        return trimmed;
    }
}
