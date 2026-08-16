package br.com.brew.brassia.container.domain;

/**
 * O contêiner foi baixado: ele é histórico, e não estoque.
 *
 * <p>Herda de {@link IllegalStateException} porque é exatamente isso — mas tem nome próprio para a borda
 * web poder responder 409 com motivo, em vez de 500 com "erro interno".
 */
public class ContainerRetiredException extends IllegalStateException {

    public ContainerRetiredException() {
        super("O contêiner foi baixado: ele é histórico, e não estoque.");
    }
}
