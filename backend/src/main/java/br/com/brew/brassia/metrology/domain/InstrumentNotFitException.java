package br.com.brew.brassia.metrology.domain;

import java.time.LocalDate;

/**
 * O instrumento não está apto para o que se pediu. Carrega a aptidão derivada e o vencimento para
 * que a resposta diga <em>por que</em> não serve, em vez de só negar.
 */
public final class InstrumentNotFitException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String instrumentCode;
    private final Fitness fitness;
    private final LocalDate calibrationDueOn;

    public InstrumentNotFitException(String instrumentCode, Fitness fitness, LocalDate calibrationDueOn,
            String message) {
        super(message);
        this.instrumentCode = instrumentCode;
        this.fitness = fitness;
        this.calibrationDueOn = calibrationDueOn;
    }

    public String instrumentCode() {
        return instrumentCode;
    }

    public Fitness fitness() {
        return fitness;
    }

    public LocalDate calibrationDueOn() {
        return calibrationDueOn;
    }
}
