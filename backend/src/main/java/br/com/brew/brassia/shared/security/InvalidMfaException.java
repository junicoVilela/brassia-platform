package br.com.brew.brassia.shared.security;

/**
 * Falha ao validar o desafio de MFA (sessão ausente ou código inválido). A borda
 * web traduz para Problem Details 401 {@code invalid_mfa}. A mensagem deve ser
 * sempre um texto seguro e voltado ao usuário — ela é exposta na resposta.
 */
public final class InvalidMfaException extends RuntimeException {
    public InvalidMfaException(String message) {
        super(message);
    }
}
