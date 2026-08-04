package br.com.brew.brassia.sensory.domain;

/**
 * Resultado pedido antes do fechamento.
 *
 * <p>É o critério da história: ver a nota alheia antes de dar a sua faz o painel convergir por
 * contágio. Enquanto a sessão está aberta só o número de fichas recebidas é público.
 */
public final class ResultsNotAvailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String sessionCode;
    private final String status;

    public ResultsNotAvailableException(String sessionCode, String status) {
        super("a sessão %s está em %s; o resultado só aparece no fechamento"
                .formatted(sessionCode, status));
        this.sessionCode = sessionCode;
        this.status = status;
    }

    public String sessionCode() {
        return sessionCode;
    }

    public String status() {
        return status;
    }
}
