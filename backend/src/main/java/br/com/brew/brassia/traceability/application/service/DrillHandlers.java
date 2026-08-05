package br.com.brew.brassia.traceability.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.traceability.DestinationSource;
import br.com.brew.brassia.traceability.LineageSource;
import br.com.brew.brassia.traceability.LineageSource.NodeType;
import br.com.brew.brassia.traceability.application.port.inbound.DrillCommands;
import br.com.brew.brassia.traceability.application.port.outbound.DrillRepository;
import br.com.brew.brassia.traceability.domain.Direction;
import br.com.brew.brassia.traceability.domain.RecallDrill;
import br.com.brew.brassia.traceability.domain.Spread;
import br.com.brew.brassia.traceability.domain.UnknownDrillException;
import br.com.brew.brassia.traceability.domain.UnknownNodeException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Comandos do simulado (FDS-004).
 *
 * <p>Nenhum deles escreve fora da própria tabela: não há expedição criada, saldo movido, quarentena
 * aberta nem pendência de comunicação. É a restrição da história — "sem afetar estoque real" — e é
 * também o que separa treinar de recolher.
 */
public final class DrillHandlers {

    static final int SCOPE_DEPTH = 6;

    private DrillHandlers() {
    }

    public static final class Start implements DrillCommands.Start {

        private final DrillRepository drills;
        private final List<LineageSource> sources;
        private final AuditTrail audit;

        public Start(DrillRepository drills, List<LineageSource> sources, AuditTrail audit) {
            this.drills = Objects.requireNonNull(drills);
            this.sources = List.copyOf(Objects.requireNonNull(sources));
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public RecallDrill handle(UUID actorId, UUID breweryId, NodeType type, UUID nodeId, String note) {
            var origin = FederatedLineageGraph.describe(sources, breweryId, type, nodeId)
                    .orElseThrow(() -> new UnknownNodeException(type, nodeId));
            var year = ZonedDateTime.now(ZoneOffset.UTC).getYear();
            var code = "SIM-%d-%04d".formatted(year, drills.nextSequence(breweryId, year));

            // O relógio começa aqui, e é o da cervejaria: o simulado mede quanto a equipe leva para
            // localizar o produto, não quanto o servidor leva para percorrer o grafo.
            var drill = RecallDrill.start(breweryId, code, origin, note, actorId, Instant.now());
            drills.insert(drill);

            audit.record(AuditEvent.success(breweryId, actorId, "traceability.drill.start",
                    "traceability.drill", drill.id().toString(),
                    Map.of("code", drill.code(), "nodeType", type.name(), "nodeId", nodeId.toString(),
                            "origin", drill.originLabel() == null ? "" : drill.originLabel())));
            return drill;
        }
    }

    public static final class Finish implements DrillCommands.Finish {

        private final DrillRepository drills;
        private final List<LineageSource> sources;
        private final List<DestinationSource> destinations;
        private final AuditTrail audit;

        public Finish(DrillRepository drills, List<LineageSource> sources,
                List<DestinationSource> destinations, AuditTrail audit) {
            this.drills = Objects.requireNonNull(drills);
            this.sources = List.copyOf(Objects.requireNonNull(sources));
            this.destinations = List.copyOf(Objects.requireNonNull(destinations));
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public void handle(UUID actorId, UUID breweryId, UUID drillId, int unitsLocated, String summary,
                String correctiveActions) {
            var drill = drills.findForUpdate(breweryId, drillId)
                    .orElseThrow(() -> new UnknownDrillException(drillId));

            // A medição é tirada no encerramento e congelada: é o resultado daquele dia.
            var measurement = DrillMeasurement.of(breweryId, drill.origin(), SCOPE_DEPTH, sources,
                    destinations);
            var at = Instant.now();
            drill.finish(actorId, measurement.unitsInScope(), unitsLocated, measurement.destinations().size(),
                    measurement.gaps().size(), summary, correctiveActions, at);
            drills.finish(drill);

            audit.record(AuditEvent.success(breweryId, actorId, "traceability.drill.finish",
                    "traceability.drill", drill.id().toString(),
                    Map.of("code", drill.code(),
                            "unitsInScope", String.valueOf(drill.unitsInScope()),
                            "unitsLocated", String.valueOf(drill.unitsLocated()),
                            "locatedPercent", String.valueOf(drill.locatedPercent()),
                            "gapsFound", String.valueOf(drill.gapsFound()),
                            "elapsedSeconds", String.valueOf(drill.elapsed(at).toSeconds()))));
        }
    }

    /** O alvo do exercício, derivado do grafo: quanto saiu, para quantos destinos, e o que falta saber. */
    record DrillMeasurement(int unitsInScope, List<DestinationSource.Destination> destinations,
            List<LineageSource.Gap> gaps) {

        static DrillMeasurement of(UUID breweryId, LineageSource.Node origin, int depth,
                List<LineageSource> sources, List<DestinationSource> destinationSources) {
            var graph = new FederatedLineageGraph(sources, breweryId);
            var spread = Spread.from(origin, Direction.FORWARD, depth, graph);
            var scope = spread.affected().stream().map(Spread.Affected::node).toList();

            var found = new java.util.ArrayList<DestinationSource.Destination>();
            for (DestinationSource source : destinationSources) {
                found.addAll(source.destinationsOf(breweryId, scope));
            }
            var gaps = new java.util.ArrayList<LineageSource.Gap>();
            for (var node : scope) {
                if (node.type() == NodeType.FINISHED_LOT) {
                    gaps.addAll(graph.gapsOf(node));
                }
            }
            // O escopo é medido no que SAIU: é o que existe para localizar lá fora. O que ficou na
            // fábrica não é objeto de recall, e contá-lo inflaria a cobertura sem que ninguém
            // tivesse procurado nada.
            var units = found.stream().mapToInt(DestinationSource.Destination::units).sum();
            return new DrillMeasurement(units, List.copyOf(found), List.copyOf(gaps));
        }
    }
}
