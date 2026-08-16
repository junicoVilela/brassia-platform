package br.com.brew.brassia.container.adapter.outbound.lineage;

import br.com.brew.brassia.container.application.port.outbound.ContainerRepository;
import br.com.brew.brassia.container.application.port.outbound.FillRepository;
import br.com.brew.brassia.traceability.LineageSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * As arestas que nascem no vasilhame (CON-002).
 *
 * <p><strong>O contêiner é nó, e não atributo do lote.</strong> Ele atravessa lotes: o mesmo keg carrega
 * um em março e outro em abril. Pendurar o vasilhame como propriedade do lote responderia "onde este lote
 * foi parar" e nunca "o que este keg já teve dentro" — e o segundo é o caminho de um recall que começa
 * numa reclamação de bar.
 *
 * <p>A aresta é {@code CONFIRMED}: o enchimento é registro do que aconteceu, com data e responsável, e
 * não intenção de encher.
 */
@Component
class ContainerLineageSource implements LineageSource {

    private final FillRepository fills;
    private final ContainerRepository containers;

    ContainerLineageSource(FillRepository fills, ContainerRepository containers) {
        this.fills = fills;
        this.containers = containers;
    }

    /** Do lote para os vasilhames que o carregaram. */
    @Override
    public List<Edge> descendantsOf(UUID breweryId, Node node) {
        if (node.type() != NodeType.FINISHED_LOT) {
            return List.of();
        }
        return fills.ofLot(breweryId, node.id()).stream()
                .map(f -> new Edge(node, container(breweryId, f.containerId()), "envase em vasilhame",
                        EdgeStrength.CONFIRMED, f.filledAt()))
                .toList();
    }

    /** Do vasilhame para os lotes que já estiveram dentro dele — todos, e não só o atual. */
    @Override
    public List<Edge> ancestorsOf(UUID breweryId, Node node) {
        if (node.type() != NodeType.CONTAINER) {
            return List.of();
        }
        return fills.historyOf(breweryId, node.id()).stream()
                .map(f -> new Edge(Node.of(NodeType.FINISHED_LOT, f.finishedLotId()), node,
                        "envase em vasilhame", EdgeStrength.CONFIRMED, f.filledAt()))
                .toList();
    }

    @Override
    public Optional<Node> describe(UUID breweryId, NodeType type, UUID id) {
        if (type != NodeType.CONTAINER) {
            return Optional.empty();
        }
        return containers.find(breweryId, id)
                .map(c -> new Node(NodeType.CONTAINER, c.id(), c.code()));
    }

    private Node container(UUID breweryId, UUID containerId) {
        return describe(breweryId, NodeType.CONTAINER, containerId)
                .orElseGet(() -> Node.of(NodeType.CONTAINER, containerId));
    }
}
