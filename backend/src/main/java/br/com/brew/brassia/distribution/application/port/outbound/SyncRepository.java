package br.com.brew.brassia.distribution.application.port.outbound;

import br.com.brew.brassia.distribution.domain.OfflineOperation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SyncRepository {

    /** Grava a operação. Falha por chave duplicada quando o mesmo aparelho reenvia. */
    void record(UUID breweryId, OfflineOperation operation);

    /** O que já foi processado daquele aparelho — a resposta do reenvio. */
    Optional<OfflineOperation> find(UUID breweryId, UUID deviceId, UUID clientOperationId);

    /** A fila de quem precisa decidir. */
    List<OfflineOperation> conflicts(UUID breweryId);

    List<OfflineOperation> ofLoad(UUID breweryId, UUID loadId);
}
