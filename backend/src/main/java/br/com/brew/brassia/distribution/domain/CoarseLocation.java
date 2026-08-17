package br.com.brew.brassia.distribution.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Onde a entrega foi registrada, <strong>com precisão reduzida de propósito</strong>.
 *
 * <p>O critério da sprint pede geolocalização minimizada, e a razão é concreta: a coordenada cheia do
 * celular do entregador, guardada parada a parada, todo dia, é um rastro de movimentação de uma pessoa —
 * e o que a operação precisa saber é outra coisa, "a entrega foi registrada no lugar certo ou a
 * quinhentos metros dali".
 *
 * <p>Três casas decimais são cerca de cem metros: o bastante para confirmar o endereço e insuficiente
 * para dizer em que ponto da calçada alguém parou. <strong>A coordenada cheia não entra aqui</strong> —
 * ela é arredondada na fronteira e a original não é guardada em lugar nenhum, porque dado que não existe
 * não vaza.
 */
public record CoarseLocation(BigDecimal latitude, BigDecimal longitude) {

    /** ~100 m. Aumentar a precisão aqui é ampliar a vigilância, e não a qualidade do registro. */
    private static final int CASAS = 3;

    public CoarseLocation {
        Objects.requireNonNull(latitude, "latitude");
        Objects.requireNonNull(longitude, "longitude");
        if (latitude.scale() > CASAS || longitude.scale() > CASAS) {
            throw new IllegalArgumentException(
                    "a coordenada precisa chegar arredondada: use CoarseLocation.of");
        }
    }

    /** A única porta de entrada: arredonda na fronteira, e a coordenada cheia morre aqui. */
    public static CoarseLocation of(BigDecimal latitude, BigDecimal longitude) {
        return new CoarseLocation(latitude.setScale(CASAS, RoundingMode.HALF_UP),
                longitude.setScale(CASAS, RoundingMode.HALF_UP));
    }
}
