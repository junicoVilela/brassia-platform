package br.com.brew.brassia.ai.domain;

import java.math.BigDecimal;

/** O orçamento de IA do mês desta cervejaria não cabe mais esta chamada (AIA-001). */
public final class AiBudgetExceededException extends RuntimeException {

    private final BigDecimal monthlyLimit;
    private final BigDecimal spent;

    public AiBudgetExceededException(BigDecimal monthlyLimit, BigDecimal spent) {
        super("o orçamento de IA deste mês foi esgotado");
        this.monthlyLimit = monthlyLimit;
        this.spent = spent;
    }

    public BigDecimal monthlyLimit() {
        return monthlyLimit;
    }

    public BigDecimal spent() {
        return spent;
    }
}
