package br.com.brew.brassia.sensory.domain;

/** Ficha enviada fora da janela de avaliação. */
public final class SessionNotOpenException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String sessionCode;
    private final String status;

    public SessionNotOpenException(String sessionCode, String status) {
        super("a sessão %s está em %s e não recebe fichas".formatted(sessionCode, status));
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
