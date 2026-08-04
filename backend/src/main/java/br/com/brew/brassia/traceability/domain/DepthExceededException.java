package br.com.brew.brassia.traceability.domain;

/**
 * Profundidade pedida acima do teto (TRC-001).
 *
 * <p>O teto não é economia de banco: é a diferença entre uma consulta e um levantamento. Sem
 * limite, a genealogia de um lote antigo com muitas gerações de levedura arrastaria metade da
 * cervejaria, e ninguém leria o resultado.
 */
public final class DepthExceededException extends RuntimeException {

    private final int requested;
    private final int maximum;

    public DepthExceededException(int requested, int maximum) {
        super("profundidade %d passa do máximo de %d saltos".formatted(requested, maximum));
        this.requested = requested;
        this.maximum = maximum;
    }

    public int requested() {
        return requested;
    }

    public int maximum() {
        return maximum;
    }
}
