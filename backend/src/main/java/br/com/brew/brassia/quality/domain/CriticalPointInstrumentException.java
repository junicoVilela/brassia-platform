package br.com.brew.brassia.quality.domain;

/**
 * Medir em ponto crítico exige instrumento apto.
 *
 * <p>É aqui que "instrumento vencido bloqueia ponto crítico" (MTR-001) vira regra executável: a
 * designação criada no cadastro metrológico encontra o ponto de controle, e a verificação acontece
 * no momento da medição — que é quando ela importa.
 */
public final class CriticalPointInstrumentException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String parameter;
    private final String instrumentCode;
    private final String fitness;

    public CriticalPointInstrumentException(String parameter, String instrumentCode, String fitness) {
        super("o ponto crítico %s exige instrumento apto; %s está %s"
                .formatted(parameter, instrumentCode, fitness));
        this.parameter = parameter;
        this.instrumentCode = instrumentCode;
        this.fitness = fitness;
    }

    public String parameter() {
        return parameter;
    }

    public String instrumentCode() {
        return instrumentCode;
    }

    public String fitness() {
        return fitness;
    }
}
