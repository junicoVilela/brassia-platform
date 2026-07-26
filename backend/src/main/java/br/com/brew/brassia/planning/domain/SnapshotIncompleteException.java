package br.com.brew.brassia.planning.domain;

/**
 * A receita publicada ainda não tem as métricas calculadas, então o snapshot da
 * OP não pode ser congelado (BOP-001). Estende {@link IllegalStateException} para
 * ser mapeada a HTTP 409 pelo tratador global.
 */
public class SnapshotIncompleteException extends IllegalStateException {
    public SnapshotIncompleteException(String message) {
        super(message);
    }
}
