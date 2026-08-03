package br.com.brew.brassia.metrology.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Ponto do certificado de calibração: quando o valor verdadeiro era {@code reference}, o
 * instrumento indicou {@code measured}.
 *
 * <p>É essa a direção que interessa na correção — parte-se do que o instrumento mostrou para
 * chegar ao que ele deveria ter mostrado.
 */
public record CurvePoint(BigDecimal reference, BigDecimal measured) {

    public CurvePoint {
        Objects.requireNonNull(reference, "valor de referência é obrigatório");
        Objects.requireNonNull(measured, "valor indicado é obrigatório");
    }
}
