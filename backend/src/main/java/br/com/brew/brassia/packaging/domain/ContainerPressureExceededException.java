package br.com.brew.brassia.packaging.domain;

import java.math.BigDecimal;

/**
 * O alvo de carbonatação gera mais pressão do que a embalagem suporta (PKG-002-A).
 *
 * <p>Não é desvio de qualidade: é garrafa estourando. Por isso recusa em vez de alertar — o alerta seria
 * lido no dia em que a linha está atrasada, e a consequência de ignorá-lo é alguém se machucando.
 */
public final class ContainerPressureExceededException extends RuntimeException {

    private final BigDecimal expectedPressureBar;
    private final BigDecimal maxPressureBar;
    private final BigDecimal referenceTempC;

    public ContainerPressureExceededException(BigDecimal expectedPressureBar, BigDecimal maxPressureBar,
            BigDecimal referenceTempC) {
        super("pressão de equilíbrio " + expectedPressureBar + " bar acima do limite da embalagem ("
                + maxPressureBar + " bar)");
        this.expectedPressureBar = expectedPressureBar;
        this.maxPressureBar = maxPressureBar;
        this.referenceTempC = referenceTempC;
    }

    public BigDecimal expectedPressureBar() {
        return expectedPressureBar;
    }

    public BigDecimal maxPressureBar() {
        return maxPressureBar;
    }

    /** A temperatura em que a conta foi feita: acima dela, a pressão sobe. */
    public BigDecimal referenceTempC() {
        return referenceTempC;
    }
}
