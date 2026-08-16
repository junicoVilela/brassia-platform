package br.com.brew.brassia.community.domain;

/**
 * A mesma pessoa já denunciou esta publicação por este motivo.
 *
 * <p>Não é limite de opinião: a contagem de denúncias é sinal, e um sinal que a mesma pessoa consegue
 * repetir deixa de medir a comunidade e passa a medir a insistência. A garantia é o índice único; esta
 * exceção é só a tradução dele.
 */
public class AlreadyReportedException extends RuntimeException {

    public AlreadyReportedException() {
        super("Você já denunciou esta publicação por este motivo.");
    }
}
