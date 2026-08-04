package br.com.brew.brassia.traceability.application.service;

import br.com.brew.brassia.traceability.LineageSource;
import br.com.brew.brassia.traceability.LineageSource.Edge;
import br.com.brew.brassia.traceability.LineageSource.Gap;
import br.com.brew.brassia.traceability.LineageSource.Node;
import br.com.brew.brassia.traceability.LineageSource.NodeType;
import br.com.brew.brassia.traceability.application.port.inbound.TraceabilityQueries;
import br.com.brew.brassia.traceability.domain.Direction;
import br.com.brew.brassia.traceability.domain.Genealogy;
import br.com.brew.brassia.traceability.domain.LineageGraph;
import br.com.brew.brassia.traceability.domain.UnknownNodeException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Reúne as fontes de linhagem num grafo só e entrega a travessia ao domínio.
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
        var root = describe(breweryId, type, nodeId)
                .orElseThrow(() -> new UnknownNodeException(type, nodeId));
        return Genealogy.walk(root, direction, depth, new FederatedGraph(breweryId));
    }

    /**
     * Pergunta a cada fonte quem sabe descrever o nó. É também o teste de existência e o de
     * tenant: nenhuma fonte descreve o que pertence a outra cervejaria, então um id alheio chega
     * aqui como inexistente — que é exatamente o que ele é, do ponto de vista de quem pergunta.
     */
    private Optional<Node> describe(UUID breweryId, NodeType type, UUID nodeId) {
        for (LineageSource source : sources) {
            var described = source.describe(breweryId, type, nodeId);
            if (described.isPresent()) {
                return described;
            }
        }
        return Optional.empty();
    }

    /** O grafo visto pelo domínio: a união do que cada módulo responde sobre o mesmo nó. */
    private final class FederatedGraph implements LineageGraph {

        private final UUID breweryId;

        private FederatedGraph(UUID breweryId) {
            this.breweryId = breweryId;
        }

        @Override
        public List<Edge> edgesOf(Node node, Direction direction) {
            var edges = new ArrayList<Edge>();
            for (LineageSource source : sources) {
                if (direction.includesForward()) {
                    edges.addAll(source.descendantsOf(breweryId, node));
                }
                if (direction.includesBackward()) {
                    edges.addAll(source.ancestorsOf(breweryId, node));
                }
            }
            return edges;
        }

        @Override
        public List<Gap> gapsOf(Node node) {
            var gaps = new ArrayList<Gap>();
            for (LineageSource source : sources) {
                gaps.addAll(source.gapsOf(breweryId, node));
            }
            return gaps;
        }
    }
}
