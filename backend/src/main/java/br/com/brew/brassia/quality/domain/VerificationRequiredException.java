package br.com.brew.brassia.quality.domain;

/**
 * Encerrar exige verificação de eficácia bem-sucedida.
 *
 * <p>Fechar com verificação negativa produziria um registro dizendo que o problema foi resolvido
 * quando ele não foi — pior que não verificar, porque a próxima auditoria encontraria a prova
 * documental de uma solução que nunca existiu.
 */
public final class VerificationRequiredException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;

    public VerificationRequiredException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
