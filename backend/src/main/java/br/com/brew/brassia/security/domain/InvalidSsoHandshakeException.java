package br.com.brew.brassia.security.domain;

/**
 * A volta de um login federado não confere (SEC-B07).
 *
 * <p>A mensagem que chega ao navegador é sempre a mesma, qualquer que seja o motivo. Distinguir "expirou"
 * de "state não bate" de "já foi usado" diria a quem está sondando exatamente qual amarra falhou — e cada
 * uma delas existe contra um ataque diferente.
 */
public class InvalidSsoHandshakeException extends RuntimeException {

    public InvalidSsoHandshakeException(String reason) {
        super(reason);
    }
}
