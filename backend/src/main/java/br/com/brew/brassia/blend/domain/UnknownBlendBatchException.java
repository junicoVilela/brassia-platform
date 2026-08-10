package br.com.brew.brassia.blend.domain;

/**
 * Um dos lotes não existe nesta cervejaria (BLD-001).
 *
 * <p>Checado na simulação, e não na execução: descobrir que o lote de destino não existe depois de
 * aprovar é descobrir tarde demais para uma operação irreversível.
 */
public final class UnknownBlendBatchException extends RuntimeException {

    public UnknownBlendBatchException(String message) {
        super(message);
    }
}
