package br.com.brew.brassia.optimization.domain;

/**
 * A receita não tem versão publicada (OPT-001).
 *
 * <p>Otimizar rascunho não faz sentido: o rascunho muda enquanto se otimiza, e o resultado apontaria para
 * uma composição que já não existe. A reprodutibilidade começa por a entrada ser estável.
 */
public final class UnpublishedRecipeException extends RuntimeException {

    public UnpublishedRecipeException(String message) {
        super(message);
    }
}
