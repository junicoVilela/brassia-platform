package br.com.brew.brassia.metrology.domain;

import java.time.LocalDate;

/**
 * Calibrar contra padrão fora da validade produz um número com aparência de rastreável e sem
 * rastreabilidade nenhuma — pior que não calibrar, porque passa a impressão de evidência.
 */
public final class ExpiredStandardException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String standardCode;
    private final LocalDate standardValidUntil;
    private final LocalDate performedOn;

    public ExpiredStandardException(String standardCode, LocalDate standardValidUntil, LocalDate performedOn) {
        super("padrão %s venceu em %s e não pode calibrar em %s"
                .formatted(standardCode, standardValidUntil, performedOn));
        this.standardCode = standardCode;
        this.standardValidUntil = standardValidUntil;
        this.performedOn = performedOn;
    }

    public String standardCode() {
        return standardCode;
    }

    public LocalDate standardValidUntil() {
        return standardValidUntil;
    }

    public LocalDate performedOn() {
        return performedOn;
    }
}
