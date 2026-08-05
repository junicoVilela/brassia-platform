package br.com.brew.brassia.traceability.application.service;

import br.com.brew.brassia.traceability.DestinationSource;
import br.com.brew.brassia.traceability.LineageSource;
import br.com.brew.brassia.traceability.LineageSource.Gap;
import br.com.brew.brassia.traceability.LineageSource.NodeType;
import br.com.brew.brassia.traceability.application.port.inbound.RecallQueries;
import br.com.brew.brassia.traceability.application.port.outbound.RecallRepository;
import br.com.brew.brassia.traceability.domain.Direction;
import br.com.brew.brassia.traceability.domain.Recall;
import br.com.brew.brassia.traceability.domain.RecallNotification;
import br.com.brew.brassia.traceability.domain.Spread;
import br.com.brew.brassia.traceability.domain.UnknownRecallException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * O dossiê do recall (FDS-003): a decisão registrada, o escopo de hoje e o que falta saber.
 *
 * <p>Escopo derivado + comunicação guardada, e a diferença entre os dois é o que a tela precisa
 * mostrar. Uma expedição que hoje está no escopo e não tem linha de comunicação significa que o
 * lote <strong>saiu depois</strong> da abertura — ela aparece separada, como descoberta, em vez de
 * entrar calada na lista dos avisados.
 *
 * <p>As lacunas são a outra metade da honestidade: lote no escopo sem expedição registrada é caixa
 * de cerveja que ninguém sabe onde está, e um dossiê que a omite mede cobertura sobre o que
 * conhece, não sobre o que existe.
 */
public final class RecallQueryHandler implements RecallQueries {

    private final RecallRepository recalls;
    private final List<LineageSource> sources;
    private final List<DestinationSource> destinations;

    public RecallQueryHandler(RecallRepository recalls, List<LineageSource> sources,
            List<DestinationSource> destinations) {
        this.recalls = Objects.requireNonNull(recalls);
        this.sources = List.copyOf(Objects.requireNonNull(sources));
        this.destinations = List.copyOf(Objects.requireNonNull(destinations));
    }

    @Override
    public List<Recall> list(UUID breweryId) {
        return recalls.findAll(breweryId);
    }

    @Override
    public Dossier dossier(UUID breweryId, UUID recallId, int depth) {
        var recall = recalls.findById(breweryId, recallId)
                .orElseThrow(() -> new UnknownRecallException(recallId));
        var graph = new FederatedLineageGraph(sources, breweryId);
        var spread = Spread.from(recall.origin(), Direction.FORWARD, depth, graph);
        var notifications = recalls.findNotifications(breweryId, recallId);

        var scope = spread.affected().stream().map(Spread.Affected::node).toList();
        var gaps = new ArrayList<Gap>();
        for (var node : scope) {
            if (node.type() == NodeType.FINISHED_LOT) {
                gaps.addAll(graph.gapsOf(node));
            }
        }

        var known = new HashSet<UUID>();
        notifications.forEach(notification -> known.add(notification.shipmentId()));
        var newDestinations = new ArrayList<NewDestination>();
        for (DestinationSource source : destinations) {
            source.destinationsOf(breweryId, scope).stream()
                    .filter(destination -> !known.contains(destination.reference()))
                    .map(destination -> new NewDestination(destination.reference(), destination.label(),
                            destination.contact(), destination.units()))
                    .forEach(newDestinations::add);
        }

        return new Dossier(recall, sortedByPendingFirst(notifications), spread,
                List.copyOf(newDestinations), List.copyOf(gaps));
    }

    /** Pendentes primeiro: a lista existe para ser trabalhada, não para ser lida. */
    private static List<RecallNotification> sortedByPendingFirst(List<RecallNotification> notifications) {
        return notifications.stream()
                .sorted((a, b) -> {
                    if (a.pending() != b.pending()) {
                        return a.pending() ? -1 : 1;
                    }
                    return a.destination().compareToIgnoreCase(b.destination());
                })
                .toList();
    }
}
