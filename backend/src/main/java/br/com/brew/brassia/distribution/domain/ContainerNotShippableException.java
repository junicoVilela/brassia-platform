package br.com.brew.brassia.distribution.domain;

/**
 * O vasilhame não pode sair da casa.
 *
 * <p><strong>É aqui que a promessa da CON-002 se cumpre.</strong> Encher precede liberar: o keg é enchido
 * na produção, antes de a qualidade assinar. Quem exige a assinatura é a saída — e a saída é esta. Deixar
 * passar significaria entregar cerveja que a própria casa ainda não disse que está boa, e descobrir isso
 * depois de o cliente ter servido.
 */
public class ContainerNotShippableException extends RuntimeException {

    private final String reasonCode;

    public ContainerNotShippableException(String reasonCode, String message) {
        super(message);
        this.reasonCode = reasonCode;
    }

    public static ContainerNotShippableException empty(String code) {
        return new ContainerNotShippableException("container_empty",
                "O vasilhame " + code + " está vazio. Carga é o que sai cheio.");
    }

    public String reasonCode() {
        return reasonCode;
    }
}
