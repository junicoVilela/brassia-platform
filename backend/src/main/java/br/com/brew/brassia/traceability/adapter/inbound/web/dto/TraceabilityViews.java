package br.com.brew.brassia.traceability.adapter.inbound.web.dto;

import br.com.brew.brassia.traceability.LineageSource.Node;
import br.com.brew.brassia.traceability.domain.Genealogy;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Respostas da rastreabilidade (TRC-001). */
public final class TraceabilityViews {

    private TraceabilityViews() {
    }

    public record NodeView(String type, UUID id, String label) {}

    /**
     * @param strength CONFIRMED (fato registrado) ou INTENDED (intenção, como a reserva de insumo)
     */
    public record EdgeView(NodeView from, NodeView to, String kind, String strength, Instant recordedAt) {}

    public record GapView(NodeView from, String expectedLink, String reason) {}

    /**
     * @param truncated verdadeiro quando o corte de profundidade escondeu parte do grafo — quem lê
     *                  precisa saber que a resposta não é o grafo inteiro
     * @param gaps      elos que deveriam existir e não existem, com o motivo
     */
    public record GenealogyView(NodeView root, String direction, int depth, boolean truncated,
            List<NodeView> nodes, List<EdgeView> edges, List<GapView> gaps) {

        public static GenealogyView of(Genealogy genealogy) {
            return new GenealogyView(node(genealogy.root()), genealogy.direction().name(), genealogy.depth(),
                    genealogy.truncated(),
                    genealogy.nodes().stream().map(GenealogyView::node).toList(),
                    genealogy.edges().stream()
                            .map(edge -> new EdgeView(node(edge.from()), node(edge.to()), edge.kind(),
                                    edge.strength().name(), edge.recordedAt()))
                            .toList(),
                    genealogy.gaps().stream()
                            .map(gap -> new GapView(node(gap.from()), gap.expectedLink(), gap.reason()))
                            .toList());
        }

        private static NodeView node(Node node) {
            return new NodeView(node.type().name(), node.id(), node.label());
        }
    }
}
