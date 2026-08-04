package br.com.brew.brassia.traceability.application.port.inbound;

import br.com.brew.brassia.traceability.LineageSource.NodeType;
import br.com.brew.brassia.traceability.domain.Direction;
import br.com.brew.brassia.traceability.domain.Genealogy;
import java.util.UUID;

/** Consulta de genealogia (TRC-001). Não há comando: o grafo é derivado do que já foi registrado. */
public interface TraceabilityQueries {

    /**
     * @throws br.com.brew.brassia.traceability.domain.UnknownNodeException  nó inexistente na cervejaria
     * @throws br.com.brew.brassia.traceability.domain.DepthExceededException profundidade acima do teto
     */
    Genealogy genealogy(UUID breweryId, NodeType type, UUID nodeId, Direction direction, int depth);
}
