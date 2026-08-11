package br.com.brew.brassia.blend.adapter.inbound.gateway;

import br.com.brew.brassia.blend.application.port.outbound.BlendRepository;
import br.com.brew.brassia.blend.domain.BlendOperation;
import br.com.brew.brassia.traceability.LineageSource;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * A ponta do blend na genealogia (BLD-001).
 *
 * <p><strong>É isto que faz o recall se recalcular sozinho.</strong> A rastreabilidade percorre as arestas
 * de todos os {@link LineageSource}; assim que uma união é executada, o lote de destino passa a ter os de
 * origem como ancestrais, e um recall que alcança qualquer um deles alcança os outros — sem que nada no
 * módulo de rastreabilidade precise saber que blend existe.
 *
 * <p>Rótulo e alergênico seguem o mesmo caminho: quem os deriva da composição atravessa a mesma
 * genealogia. Recalcular não é um passo que alguém dispara; é consequência de a aresta existir.
 *
 * <p><strong>Só operações executadas contribuem.</strong> Simulada e aprovada não moveram cerveja
 * nenhuma. Uma aresta prematura faria o recall exagerar — e recall que exagera é descartado por quem o
 * recebe, o que o torna tão inútil quanto um que falta.
 */
@Component
class BlendLineageAdapter implements LineageSource {

    private static final String MERGE_EDGE = "unido em";
    private static final String SPLIT_EDGE = "dividido em";

    private final BlendRepository operations;

    BlendLineageAdapter(BlendRepository operations) {
        this.operations = operations;
    }

    /** O que veio deste lote: se ele foi origem de uma operação, os destinos dela descendem dele. */
    @Override
    public List<Edge> descendantsOf(UUID breweryId, Node node) {
        if (node.type() != NodeType.BATCH) {
            return List.of();
        }
        var edges = new ArrayList<Edge>();
        for (var operation : operations.executedTouching(breweryId, node.id())) {
            if (isInput(operation, node.id())) {
                // Destinos são os lotes que já existiam E os que a operação criou. Deixar os criados de
                // fora pararia a travessia exatamente no lote que a união produziu — que é o que sai da
                // fábrica e o que um recall precisa alcançar.
                for (var destination : operation.destinationBatchIds()) {
                    edges.add(edge(operation, node, batch(destination)));
                }
            }
        }
        return edges;
    }

    /** O que originou este lote: se ele foi destino, as origens da operação o antecedem. */
    @Override
    public List<Edge> ancestorsOf(UUID breweryId, Node node) {
        if (node.type() != NodeType.BATCH) {
            return List.of();
        }
        var edges = new ArrayList<Edge>();
        for (var operation : operations.executedTouching(breweryId, node.id())) {
            if (isOutput(operation, node.id())) {
                for (var input : operation.inputs()) {
                    edges.add(edge(operation, batch(input.batchId()), node));
                }
            }
        }
        return edges;
    }

    private Edge edge(BlendOperation operation, Node from, Node to) {
        // CONFIRMED: é registro do que aconteceu, não inferência. A execução foi assinada por alguém.
        return new Edge(from, to, kindOf(operation), EdgeStrength.CONFIRMED,
                operation.executedAt().orElseThrow());
    }

    private static String kindOf(BlendOperation operation) {
        return switch (operation.kind()) {
            case MERGE -> MERGE_EDGE;
            case SPLIT -> SPLIT_EDGE;
        };
    }

    private static Node batch(UUID batchId) {
        // Sem rótulo: quem tem o código do lote é a produção, e inventá-lo aqui duplicaria a fonte.
        return Node.of(NodeType.BATCH, batchId);
    }

    private static boolean isInput(BlendOperation operation, UUID batchId) {
        return operation.inputs().stream().anyMatch(m -> m.batchId().equals(batchId));
    }

    private static boolean isOutput(BlendOperation operation, UUID batchId) {
        return operation.destinationBatchIds().stream().anyMatch(id -> id.equals(batchId));
    }
}
