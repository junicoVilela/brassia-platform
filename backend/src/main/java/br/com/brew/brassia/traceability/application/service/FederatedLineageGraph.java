package br.com.brew.brassia.traceability.application.service;

import br.com.brew.brassia.traceability.LineageSource;
import br.com.brew.brassia.traceability.LineageSource.Edge;
import br.com.brew.brassia.traceability.LineageSource.Gap;
import br.com.brew.brassia.traceability.LineageSource.Node;
import br.com.brew.brassia.traceability.LineageSource.NodeType;
import br.com.brew.brassia.traceability.domain.Direction;
import br.com.brew.brassia.traceability.domain.LineageGraph;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * O grafo visto pelo domínio: a união do que cada módulo responde sobre o mesmo nó.
 *
 * <p>Não sabe quais módulos existem — recebe a lista de fontes e pergunta a todas. Um módulo novo
 * passa a contribuir arestas sem que uma linha daqui mude, e um que some vira lacuna em vez de
 * quebrar a consulta.
 *
 * <p>Serve tanto à genealogia (TRC-001) quanto à propagação da quarentena (FDS-002): as duas
 * percorrem o mesmo grafo, e ter duas montagens dele deixaria a contenção enxergar uma cadeia
 * diferente da que a investigação mostra na tela.
 */
final class FederatedLineageGraph implements LineageGraph {

    private final List<LineageSource> sources;
    private final UUID breweryId;

    FederatedLineageGraph(List<LineageSource> sources, UUID breweryId) {
        this.sources = Objects.requireNonNull(sources);
        this.breweryId = Objects.requireNonNull(breweryId);
    }

    /**
     * Pergunta a cada fonte quem sabe descrever o nó. É também o teste de existência e o de tenant:
     * nenhuma fonte descreve o que pertence a outra cervejaria, então um id alheio chega como
     * inexistente — que é exatamente o que ele é, do ponto de vista de quem pergunta.
     */
    static Optional<Node> describe(List<LineageSource> sources, UUID breweryId, NodeType type, UUID nodeId) {
        for (LineageSource source : sources) {
            var described = source.describe(breweryId, type, nodeId);
            if (described.isPresent()) {
                return described;
            }
        }
        return Optional.empty();
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
