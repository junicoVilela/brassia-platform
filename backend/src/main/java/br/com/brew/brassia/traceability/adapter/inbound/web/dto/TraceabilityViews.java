package br.com.brew.brassia.traceability.adapter.inbound.web.dto;

import br.com.brew.brassia.traceability.LineageSource;
import br.com.brew.brassia.traceability.LineageSource.Node;
import br.com.brew.brassia.traceability.application.port.inbound.DrillQueries;
import br.com.brew.brassia.traceability.application.port.inbound.QuarantineQueries;
import br.com.brew.brassia.traceability.application.port.inbound.RecallQueries;
import br.com.brew.brassia.traceability.domain.Genealogy;
import br.com.brew.brassia.traceability.domain.Quarantine;
import br.com.brew.brassia.traceability.domain.Recall;
import br.com.brew.brassia.traceability.domain.RecallDrill;
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

    // --- simulado de recall (FDS-004) ---

    public record StartDrillRequest(@NotNull LineageSource.NodeType nodeType, @NotNull UUID nodeId,
            @Size(max = 500) String note) {}

    /**
     * @param correctiveActions texto livre, mantido para o simulado que não gerou ação. Não pode vir
     *                          junto com {@code nonConformityId}: ação escrita como texto E como item de
     *                          CAPA deixaria quem lê sem saber qual é a de verdade
     * @param nonConformityId a NC onde as ações viram itens de CAPA (FDS-004-A). Quem encerra escolhe uma
     *                        aberta ou abre uma antes — o simulado não abre NC sozinho, porque isso
     *                        exigiria o sistema decidir a severidade
     */
    public record FinishDrillRequest(@jakarta.validation.constraints.Min(0) int unitsLocated,
            @NotBlank @Size(max = 1000) String summary,
            @Size(max = 2000) String correctiveActions,
            java.util.UUID nonConformityId,
            java.util.List<@jakarta.validation.Valid CapaActionRequest> capaActions) {}

    /** Ação de CAPA nascida de uma lacuna do simulado: com tipo, dono e prazo. */
    public record CapaActionRequest(@NotBlank String kind, @NotBlank @Size(max = 1000) String description,
            @NotBlank @Size(max = 120) String owner,
            @jakarta.validation.constraints.NotNull java.time.LocalDate dueOn) {}

    /**
     * @param locatedPercent nulo quando não havia nada no escopo: não localizar o que não existe não
     *                       é cobertura perfeita, é simulado sem objeto
     * @param elapsedSeconds tempo da cervejaria, não do sistema — é o que a norma cobra
     * @param nonConformityId a NC onde as ações corretivas viraram itens de CAPA (FDS-004-A); nula quando
     *                        o simulado não gerou ação, e nunca preenchida junto com o texto livre
     */
    public record DrillView(UUID id, String code, NodeView origin, String note, String status,
            Instant startedAt, Instant finishedAt, Integer unitsInScope, Integer unitsLocated,
            Integer locatedPercent, Integer destinationsReached, Integer gapsFound, String summary,
            String correctiveActions, UUID nonConformityId, long elapsedSeconds) {

        public static DrillView of(RecallDrill drill, long elapsedSeconds) {
            return new DrillView(drill.id(), drill.code(),
                    new NodeView(drill.nodeType().name(), drill.nodeId(), drill.originLabel()),
                    drill.note(), drill.status().name(), drill.startedAt(), drill.finishedAt(),
                    drill.unitsInScope(), drill.unitsLocated(), drill.locatedPercent(),
                    drill.destinationsReached(), drill.gapsFound(), drill.summary(),
                    drill.correctiveActions(), drill.nonConformityId().orElse(null), elapsedSeconds);
        }

        public static List<DrillView> of(List<RecallDrill> drills, Instant now) {
            return drills.stream()
                    .map(drill -> of(drill, drill.elapsed(now).toSeconds()))
                    .toList();
        }
    }

    /** @param findings lacunas viradas do avesso: o que fazer para a cobertura ser maior */
    public record DrillReportView(DrillView drill, int unitsInScope, int destinationsReached,
            List<DrillDestinationView> destinations, List<GapView> gaps, List<String> findings) {

        public static DrillReportView of(DrillQueries.Report report) {
            return new DrillReportView(DrillView.of(report.drill(), report.elapsedSeconds()),
                    report.unitsInScope(), report.destinationsReached(),
                    report.destinations().stream()
                            .map(destination -> new DrillDestinationView(destination.reference(),
                                    destination.label(), destination.contact(), destination.units()))
                            .toList(),
                    report.gaps().stream()
                            .map(gap -> new GapView(
                                    new NodeView(gap.from().type().name(), gap.from().id(), gap.from().label()),
                                    gap.expectedLink(), gap.reason()))
                            .toList(),
                    report.findings());
        }
    }

    public record DrillDestinationView(UUID reference, String destination, String contact, int units) {}
}
