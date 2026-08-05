package br.com.brew.brassia.traceability.adapter.inbound.web.dto;

import br.com.brew.brassia.traceability.LineageSource;
import br.com.brew.brassia.traceability.LineageSource.Node;
import br.com.brew.brassia.traceability.application.port.inbound.QuarantineQueries;
import br.com.brew.brassia.traceability.application.port.inbound.RecallQueries;
import br.com.brew.brassia.traceability.domain.Genealogy;
import br.com.brew.brassia.traceability.domain.Quarantine;
import br.com.brew.brassia.traceability.domain.Recall;
import br.com.brew.brassia.traceability.domain.RecallNotification;
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

    // --- recall (FDS-003) ---

    public record OpenRecallRequest(@NotNull LineageSource.NodeType nodeType, @NotNull UUID nodeId,
            @NotBlank @Size(max = 1000) String reason) {}

    public record NotifyRequest(@NotBlank @Size(max = 40) String channel,
            @Size(max = 500) String note) {}

    public record CloseRecallRequest(@NotBlank @Size(max = 1000) String summary) {}

    public record RecallView(UUID id, String code, NodeView origin, String reason, String status,
            Instant openedAt, Instant closedAt, String closingSummary) {

        public static RecallView of(Recall recall) {
            return new RecallView(recall.id(), recall.code(),
                    new NodeView(recall.nodeType().name(), recall.nodeId(), recall.originLabel()),
                    recall.reason(), recall.status().name(), recall.openedAt(), recall.closedAt(),
                    recall.closingSummary());
        }

        public static List<RecallView> of(List<Recall> recalls) {
            return recalls.stream().map(RecallView::of).toList();
        }
    }

    /** Um destino do dossiê e o que se fez a respeito — a parte guardada do recall. */
    public record NotificationView(UUID id, UUID shipmentId, String finishedLotCode, String destination,
            String contact, int units, String status, String channel, String note, Instant notifiedAt) {

        public static NotificationView of(RecallNotification notification) {
            return new NotificationView(notification.id(), notification.shipmentId(),
                    notification.finishedLotCode(), notification.destination(), notification.contact(),
                    notification.units(), notification.status().name(), notification.channel(),
                    notification.note(), notification.notifiedAt());
        }
    }

    /**
     * @param newDestinations expedições que hoje estão no escopo e não estavam na abertura — o lote
     *                        saiu depois; aparecem separadas em vez de entrar caladas entre os avisados
     * @param gaps            lotes do escopo sem expedição registrada: não se sabe onde estão
     * @param coverage        percentual de destinos conhecidos já comunicados
     */
    public record RecallDossierView(RecallView recall, List<NotificationView> notifications,
            int pending, int coverage, boolean truncated, List<AffectedView> scope,
            List<NewDestinationView> newDestinations, List<GapView> gaps) {

        public static RecallDossierView of(RecallQueries.Dossier dossier) {
            var notifications = dossier.notifications().stream().map(NotificationView::of).toList();
            var pending = (int) dossier.notifications().stream().filter(RecallNotification::pending).count();
            var total = notifications.size();
            var coverage = total == 0 ? 100 : (total - pending) * 100 / total;
            return new RecallDossierView(RecallView.of(dossier.recall()), notifications, pending, coverage,
                    dossier.spread().truncated(),
                    dossier.spread().affected().stream()
                            .map(affected -> new AffectedView(
                                    new NodeView(affected.node().type().name(), affected.node().id(),
                                            affected.node().label()),
                                    affected.suspected()))
                            .toList(),
                    dossier.newDestinations().stream()
                            .map(destination -> new NewDestinationView(destination.shipmentId(),
                                    destination.destination(), destination.contact(), destination.units()))
                            .toList(),
                    dossier.gaps().stream()
                            .map(gap -> new GapView(
                                    new NodeView(gap.from().type().name(), gap.from().id(), gap.from().label()),
                                    gap.expectedLink(), gap.reason()))
                            .toList());
        }
    }

    public record NewDestinationView(UUID shipmentId, String destination, String contact, int units) {}
}
