package br.com.brew.brassia.security.application.port.outbound;

/**
 * Hash de tokens de conta (convite/verificação/reset). Tokens são de alta
 * entropia, portanto usam hash rápido (não o encoder de senha).
 */
public interface TokenHasher {
    String hash(String rawToken);
}
