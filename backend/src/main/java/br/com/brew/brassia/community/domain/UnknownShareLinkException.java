package br.com.brew.brassia.community.domain;

/**
 * O link não existe, expirou, foi revogado, ou a publicação dele fechou (COM-002).
 *
 * <p><strong>Uma exceção para os quatro casos, de propósito.</strong> Dizer "expirado" a quem tem um
 * token inventado confirma que aquele token um dia existiu; dizer "revogado" conta que houve um
 * compartilhamento e que alguém se arrependeu. Para quem está do lado de fora, o link simplesmente não
 * abre — e o autor, que é quem precisa saber o motivo, vê o estado de cada link na própria lista.
 */
public class UnknownShareLinkException extends RuntimeException {

    public UnknownShareLinkException() {
        super("este link não dá acesso a nada");
    }
}
