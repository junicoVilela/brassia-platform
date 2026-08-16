package br.com.brew.brassia.container.domain;

/**
 * O contêiner não pode receber cerveja agora — e a mensagem diz por quê.
 *
 * <p>Recusar sem motivo faria o operador tentar de novo com outro keg até um passar, sem nunca saber o
 * que estava errado com o primeiro.
 */
public class ContainerNotFillableException extends RuntimeException {

    private final String reasonCode;

    private ContainerNotFillableException(String reasonCode, String message) {
        super(message);
        this.reasonCode = reasonCode;
    }

    public static ContainerNotFillableException damaged() {
        return new ContainerNotFillableException("damaged",
                "O contêiner está avariado. Encher um vasilhame com vazamento perde a cerveja e o tempo.");
    }

    public static ContainerNotFillableException condemned() {
        return new ContainerNotFillableException("condemned",
                "O contêiner foi condenado e só espera baixa.");
    }

    public static ContainerNotFillableException inspectionExpired() {
        return new ContainerNotFillableException("inspection_expired",
                "A inspeção do vasilhame está vencida. Vaso de pressão sem inspeção em dia é risco "
                        + "físico, e não pendência de papel.");
    }

    public static ContainerNotFillableException notReady(ContainerState state) {
        return new ContainerNotFillableException("not_ready",
                "O contêiner está em " + state + " — só se enche o que está vazio e liberado.");
    }

    public String reasonCode() {
        return reasonCode;
    }
}
