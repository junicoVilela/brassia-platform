package br.com.brew.brassia.ai.domain;

/**
 * O modelo não respondeu — porque está desligado, porque recusou ou porque estourou o prazo
 * (AIA-001).
 *
 * <p>É recusa explícita, e é assim de propósito: um gateway que devolvesse texto vazio ou um objeto
 * default quando o provedor está fora faria o chamador seguir adiante achando que tem resposta. O
 * fluxo continua — o erro é tratado na borda e vira Problem Details —, mas ninguém confunde silêncio
 * com resposta.
 */
public final class AiUnavailableException extends RuntimeException {

    private final InvocationStatus status;

    public AiUnavailableException(InvocationStatus status, String message) {
        super(message);
        this.status = status;
    }

    public InvocationStatus status() {
        return status;
    }

    public boolean disabled() {
        return status == InvocationStatus.PROVIDER_DISABLED;
    }
}
