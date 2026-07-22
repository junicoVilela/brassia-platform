package br.com.brew.brassia.shared.security;

/** Limite de tentativas excedido (SEC-012); mapeado para HTTP 429. */
public final class TooManyRequestsException extends RuntimeException {
    public TooManyRequestsException(String message) {
        super(message);
    }
}
