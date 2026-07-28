package br.com.brew.brassia.sanitation.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Verificação de um ciclo concluído (CLN-004): enxágue, inspeção visual, ATP e micro.
 * O ATP é aprovado quando a leitura (RLU) fica no limite ou abaixo. A verificação só
 * "passa" com as quatro checagens aprovadas — sanitização não passa com limpeza reprovada.
 */
public record Verification(boolean rinseOk, boolean visualOk, BigDecimal atpRlu, BigDecimal atpThreshold,
        boolean microOk, Instant verifiedAt) {

    public Verification {
        Objects.requireNonNull(atpRlu, "atpRlu");
        Objects.requireNonNull(atpThreshold, "atpThreshold");
        if (atpRlu.signum() < 0 || atpThreshold.signum() < 0) {
            throw new IllegalArgumentException("valores de ATP não podem ser negativos");
        }
        Objects.requireNonNull(verifiedAt, "verifiedAt");
    }

    public static Verification of(boolean rinseOk, boolean visualOk, BigDecimal atpRlu, BigDecimal atpThreshold,
            boolean microOk) {
        return new Verification(rinseOk, visualOk, atpRlu, atpThreshold, microOk, Instant.now());
    }

    public boolean atpOk() {
        return atpRlu.compareTo(atpThreshold) <= 0;
    }

    public boolean passed() {
        return rinseOk && visualOk && atpOk() && microOk;
    }
}
