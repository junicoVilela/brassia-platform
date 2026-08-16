package br.com.brew.brassia.community.domain;

/**
 * O autor tentou avaliar a própria publicação (COM-005).
 *
 * <p>Não é desconfiança: a nota do autor não informa ninguém, e uma média que a inclui mede outra coisa.
 * O mesmo vale para denunciar a si mesmo — se ele quer tirar do ar, o botão é despublicar.
 */
public class SelfRatingException extends RuntimeException {

    public SelfRatingException(String action) {
        super("não faz sentido " + action + " a própria publicação");
    }
}
