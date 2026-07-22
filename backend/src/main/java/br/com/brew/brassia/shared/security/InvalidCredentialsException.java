package br.com.brew.brassia.shared.security;

/**
 * Credenciais de login inválidas. A borda web traduz para Problem Details 401
 * com mensagem genérica, sem vazar se a conta existe ou qual campo falhou.
 */
public final class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String message) {
        super(message);
    }
}
