package br.com.brew.brassia.community.domain;

/**
 * Tentaram aceitar ou recusar algo que não pediu decisão (COM-004).
 *
 * <p>Um comentário é observação: ele não propõe mudança, então não há o que aceitar. Deixar passar faria
 * a tela oferecer dois botões sem sentido e a contagem de "pendentes" incluir elogios.
 */
public class NotDecidableException extends RuntimeException {

    public NotDecidableException(ContributionKind kind) {
        super("um " + (kind == ContributionKind.COMMENT ? "comentário" : "item") + " não se aceita nem "
                + "se recusa");
    }
}
