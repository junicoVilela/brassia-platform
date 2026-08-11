package br.com.brew.brassia.blend.application.port.outbound;

import br.com.brew.brassia.blend.domain.BlendOperation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BlendRepository {

    void insert(BlendOperation operation);

    /** Grava só o que muda: estado, aprovação e execução. Movimentos e motivo não se editam. */
    void updateProgress(BlendOperation operation);

    Optional<BlendOperation> find(UUID breweryId, UUID operationId);

    /** Carregada com a linha travada, para que duas execuções simultâneas não passem as duas. */
    Optional<BlendOperation> findForUpdate(UUID breweryId, UUID operationId);

    List<BlendOperation> list(UUID breweryId);

    /** Liga o lote criado à saída planejada que o justificou. Só depois da execução. */
    void linkResultBatch(UUID operationId, int seq, UUID batchId);

    /** Operações executadas que tocam este lote — de qualquer um dos lados, inclusive como resultado. */
    List<BlendOperation> executedTouching(UUID breweryId, UUID batchId);
}
