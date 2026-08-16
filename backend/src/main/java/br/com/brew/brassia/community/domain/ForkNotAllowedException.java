package br.com.brew.brassia.community.domain;

/**
 * A publicação não autoriza cópia (COM-003).
 *
 * <p>Duas causas, e a mensagem diz qual: a licença não permite derivados, ou a publicação não alcança
 * quem pediu. A segunda não é sobre licença — é a matriz de visibilidade de novo: não se forka o que não
 * se pode ler.
 */
public class ForkNotAllowedException extends RuntimeException {

    public ForkNotAllowedException(String reason) {
        super(reason);
    }

    public static ForkNotAllowedException license(RecipeLicense license) {
        return new ForkNotAllowedException(
                "a licença " + license.label() + " não autoriza cópia desta receita");
    }

    public static ForkNotAllowedException unreachable() {
        return new ForkNotAllowedException("esta publicação não está acessível para cópia");
    }
}
