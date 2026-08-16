package br.com.brew.brassia.distribution.domain;

import java.math.BigDecimal;

/**
 * Não cabe.
 *
 * <p>A mensagem diz <strong>quanto</strong> passou, e não só que passou: "excedeu a capacidade" manda o
 * operador tirar itens no chute até caber.
 */
public class LoadCapacityExceededException extends RuntimeException {

    private final BigDecimal excessLiters;

    public LoadCapacityExceededException(BigDecimal excessLiters) {
        super("A carga passa da capacidade do veículo em " + excessLiters.stripTrailingZeros()
                .toPlainString() + " L.");
        this.excessLiters = excessLiters;
    }

    public BigDecimal excessLiters() {
        return excessLiters;
    }
}
