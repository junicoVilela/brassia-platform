package br.com.brew.brassia.production.application.port.outbound;

import br.com.brew.brassia.production.domain.Measurement;
import java.util.List;
import java.util.UUID;

public interface MeasurementRepository {
    void insert(Measurement measurement);

    /**
     * Insere, ou não insere nada se aquele {@code clientRequestId} já foi gravado (PWA-002).
     *
     * <p>A decisão é da restrição única do banco, não de uma consulta anterior: verificar antes e inserir
     * depois deixa uma janela entre as duas operações, e é nela que caem duas tentativas simultâneas da
     * mesma fila — um retry disparado enquanto o primeiro envio ainda estava em voo.
     *
     * @return {@code true} quando a medição entrou; {@code false} quando já existia.
     */
    boolean insertIfAbsent(Measurement measurement);

    /** A medição já gravada para aquela identidade de apontamento, se houver. */
    java.util.Optional<Measurement> byClientRequestId(UUID breweryId, String clientRequestId);

    List<Measurement> findByBatch(UUID breweryId, UUID batchId);

    boolean existsInBatch(UUID breweryId, UUID batchId, UUID measurementId);
}
