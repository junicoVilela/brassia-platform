package br.com.brew.brassia.traceability.application.service;

import br.com.brew.brassia.traceability.LineageSource;
import br.com.brew.brassia.traceability.LineageSource.NodeType;
import br.com.brew.brassia.traceability.application.port.inbound.TraceabilityQueries;
import br.com.brew.brassia.traceability.domain.Direction;
import br.com.brew.brassia.traceability.domain.Genealogy;
import br.com.brew.brassia.traceability.domain.UnknownNodeException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Entrega a travessia ao domínio sobre o grafo federado das fontes de linhagem.
 *
 * <p>Não sabe quais módulos existem: recebe a lista de implementações e pergunta a todas. Um
 * módulo novo passa a contribuir arestas sem que uma linha daqui mude, e um módulo que some deixa
 * de contribuir sem quebrar a consulta — vira lacuna, que é o comportamento correto.
 */
public final class GenealogyQueryHandler implements TraceabilityQueries {

    private final List<LineageSource> sources;

    public GenealogyQueryHandler(List<LineageSource> sources) {
        this.sources = List.copyOf(Objects.requireNonNull(sources));
    }

    @Override
    public Genealogy genealogy(UUID breweryId, NodeType type, UUID nodeId, Direction direction, int depth) {
        var root = FederatedLineageGraph.describe(sources, breweryId, type, nodeId)
                .orElseThrow(() -> new UnknownNodeException(type, nodeId));
        return Genealogy.walk(root, direction, depth, new FederatedLineageGraph(sources, breweryId));
    }
}
