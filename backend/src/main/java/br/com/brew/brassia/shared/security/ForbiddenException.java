package br.com.brew.brassia.shared.security;

/**
 * Negação de autorização na aplicação/domínio. A borda web traduz para
 * Problem Details 403; não depende do Spring Security.
 */
public final class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
