package br.com.brew.brassia.traceability.adapter.inbound.web.dto;

import br.com.brew.brassia.traceability.LineageSource;
import br.com.brew.brassia.traceability.LineageSource.Node;
import br.com.brew.brassia.traceability.application.port.inbound.QuarantineQueries;
import br.com.brew.brassia.traceability.domain.Genealogy;
import br.com.brew.brassia.traceability.domain.Quarantine;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

    /** Quarentena (FDS-002). */
    public record QuarantineView(UUID id, NodeView origin, String reason, String status,
            Instant openedAt, Instant releasedAt, String releaseJustification) {

        public static QuarantineView of(Quarantine quarantine) {
            return new QuarantineView(quarantine.id(), node(quarantine.origin()), quarantine.reason(),
                    quarantine.status().name(), quarantine.openedAt(), quarantine.releasedAt(),
                    quarantine.releaseJustification());
        }

        public static List<QuarantineView> of(List<Quarantine> quarantines) {
            return quarantines.stream().map(QuarantineView::of).toList();
        }

        private static NodeView node(Node node) {
            return new NodeView(node.type().name(), node.id(), node.label());
        }
    }

    /**
     * @param suspected verdadeiro quando o caminho até o nó passa por intenção e não por fato
     *                  registrado. Bloqueia igual, e é justamente por isso que precisa aparecer:
     *                  quem investiga tem de saber onde apertar primeiro.
     */
    public record AffectedView(NodeView node, boolean suspected) {}

    /**
     * @param truncated verdadeiro quando o corte de profundidade escondeu parte do alcance — uma
     *                  contenção que parece completa sem ser é pior do que uma declaradamente parcial
     */
    public record QuarantineDetailView(QuarantineView quarantine, boolean truncated,
            List<AffectedView> affected) {

        public static QuarantineDetailView of(QuarantineQueries.Detail detail) {
            return new QuarantineDetailView(QuarantineView.of(detail.quarantine()),
                    detail.spread().truncated(),
                    detail.spread().affected().stream()
                            .map(affected -> new AffectedView(
                                    new NodeView(affected.node().type().name(), affected.node().id(),
                                            affected.node().label()),
                                    affected.suspected()))
                            .toList());
        }
    }

    public record OpenQuarantineRequest(@NotNull LineageSource.NodeType nodeType, @NotNull UUID nodeId,
            @NotBlank @Size(max = 500) String reason) {}

    public record ReleaseQuarantineRequest(@NotBlank @Size(max = 500) String justification) {}
}
