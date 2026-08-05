package br.com.brew.brassia.traceability.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.traceability.DestinationSource;
import br.com.brew.brassia.traceability.LineageSource;
import br.com.brew.brassia.traceability.LineageSource.Node;
import br.com.brew.brassia.traceability.LineageSource.NodeType;
import br.com.brew.brassia.traceability.application.port.inbound.RecallCommands;
import br.com.brew.brassia.traceability.application.port.outbound.RecallRepository;
import br.com.brew.brassia.traceability.domain.Direction;
import br.com.brew.brassia.traceability.domain.Recall;
import br.com.brew.brassia.traceability.domain.RecallNotification;
import br.com.brew.brassia.traceability.domain.Spread;
import br.com.brew.brassia.traceability.domain.UnknownNodeException;
import br.com.brew.brassia.traceability.domain.UnknownRecallException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Comandos do recall (FDS-003).
 *
 * <p>Abrir é o comando que mais decide. Ele deriva o escopo do grafo, encontra as expedições que
 * saíram desses lotes e <strong>materializa uma linha por destino</strong> — a única coisa do
 * recall que é guardada, porque avisar um cliente é fato sobre o que a cervejaria fez, não
 * consequência do grafo. Tudo num commit: um recall aberto sem a lista de quem avisar seria um
 * recall que ninguém consegue executar.
 */
public final class RecallHandlers {

    /** Mesmo padrão da genealogia e da quarentena: as três enxergam a mesma cadeia. */
    static final int SCOPE_DEPTH = 6;

    private RecallHandlers() {
    }

    public static final class Open implements RecallCommands.Open {

        private final RecallRepository recalls;
        private final List<LineageSource> sources;
        private final List<DestinationSource> destinations;
        private final AuditTrail audit;

        public Open(RecallRepository recalls, List<LineageSource> sources,
                List<DestinationSource> destinations, AuditTrail audit) {
            this.recalls = Objects.requireNonNull(recalls);
            this.sources = List.copyOf(Objects.requireNonNull(sources));
            this.destinations = List.copyOf(Objects.requireNonNull(destinations));
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public Recall handle(UUID actorId, UUID breweryId, NodeType type, UUID nodeId, String reason) {
            var origin = FederatedLineageGraph.describe(sources, breweryId, type, nodeId)
                    .orElseThrow(() -> new UnknownNodeException(type, nodeId));

            var spread = Spread.from(origin, Direction.FORWARD, SCOPE_DEPTH,
                    new FederatedLineageGraph(sources, breweryId));
            var year = ZonedDateTime.now(ZoneOffset.UTC).getYear();
            var code = "REC-%d-%04d".formatted(year, recalls.nextSequence(breweryId, year));
            var recall = Recall.open(breweryId, code, origin, reason, actorId, Instant.now());

            var notifications = notificationsFor(breweryId, recall.id(), spread);
            recalls.insert(recall, notifications);

            audit.record(AuditEvent.success(breweryId, actorId, "traceability.recall.open",
                    "traceability.recall", recall.id().toString(),
                    Map.of("code", recall.code(), "nodeType", type.name(), "nodeId", nodeId.toString(),
                            "origin", recall.originLabel() == null ? "" : recall.originLabel(),
                            "destinations", String.valueOf(notifications.size()),
                            "reason", recall.reason())));
            return recall;
        }

        /** Um destino por saída alcançada; o rótulo do nó de origem vem congelado junto. */
        private List<RecallNotification> notificationsFor(UUID breweryId, UUID recallId, Spread spread) {
            var scope = spread.affected().stream().map(Spread.Affected::node).toList();
            var notifications = new ArrayList<RecallNotification>();
            for (DestinationSource source : destinations) {
                for (var destination : source.destinationsOf(breweryId, scope)) {
                    notifications.add(RecallNotification.pending(recallId, destination.reference(),
                            label(destination.origin()), destination.label(), destination.contact(),
                            destination.units()));
                }
            }
            return notifications;
        }

        private static String label(Node node) {
            return node.label() == null ? node.id().toString() : node.label();
        }
    }

    public static final class RecordNotification implements RecallCommands.RecordNotification {

        private final RecallRepository recalls;
        private final AuditTrail audit;

        public RecordNotification(RecallRepository recalls, AuditTrail audit) {
            this.recalls = Objects.requireNonNull(recalls);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public void handle(UUID actorId, UUID breweryId, UUID recallId, UUID notificationId, String channel,
                String note) {
            var recall = recalls.findById(breweryId, recallId)
                    .orElseThrow(() -> new UnknownRecallException(recallId));
            if (!recall.open()) {
                throw new IllegalStateException("o recall já foi encerrado");
            }
            var notification = recalls.findNotification(breweryId, recallId, notificationId)
                    .orElseThrow(() -> new IllegalArgumentException("destino inexistente neste recall"));

            notification.notified(actorId, channel, note, Instant.now());
            recalls.updateNotification(breweryId, notification);

            audit.record(AuditEvent.success(breweryId, actorId, "traceability.recall.notify",
                    "traceability.recall", recall.id().toString(),
                    Map.of("code", recall.code(), "destination", notification.destination(),
                            "channel", notification.channel(),
                            "note", notification.note() == null ? "" : notification.note())));
        }
    }

    public static final class Close implements RecallCommands.Close {

        private final RecallRepository recalls;
        private final AuditTrail audit;

        public Close(RecallRepository recalls, AuditTrail audit) {
            this.recalls = Objects.requireNonNull(recalls);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public void handle(UUID actorId, UUID breweryId, UUID recallId, String summary) {
            var recall = recalls.findForUpdate(breweryId, recallId)
                    .orElseThrow(() -> new UnknownRecallException(recallId));
            var pending = recalls.countPending(breweryId, recallId);
            var version = recall.version();

            recall.close(actorId, summary, pending, Instant.now());
            if (!recalls.updateStatus(recall, version)) {
                throw new IllegalStateException("recall alterado por outra operação; tente novamente");
            }

            audit.record(AuditEvent.success(breweryId, actorId, "traceability.recall.close",
                    "traceability.recall", recall.id().toString(),
                    Map.of("code", recall.code(), "summary", recall.closingSummary())));
        }
    }
}
