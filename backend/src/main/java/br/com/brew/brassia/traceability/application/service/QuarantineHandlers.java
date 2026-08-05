package br.com.brew.brassia.traceability.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.traceability.LineageSource;
import br.com.brew.brassia.traceability.LineageSource.NodeType;
import br.com.brew.brassia.traceability.application.port.inbound.QuarantineCommands;
import br.com.brew.brassia.traceability.application.port.outbound.QuarantineRepository;
import br.com.brew.brassia.traceability.domain.AlreadyQuarantinedException;
import br.com.brew.brassia.traceability.domain.Quarantine;
import br.com.brew.brassia.traceability.domain.UnknownNodeException;
import br.com.brew.brassia.traceability.domain.UnknownQuarantineException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Comandos da quarentena (FDS-002).
 *
 * <p>Abrir e liberar são as duas metades de uma decisão de contenção, e as duas são auditadas com o
 * motivo por extenso: meses depois, o que se precisa responder é <em>quem afirmou o quê</em>.
 */
public final class QuarantineHandlers {

    private QuarantineHandlers() {
    }

    public static final class Open implements QuarantineCommands.Open {

        private final QuarantineRepository quarantines;
        private final List<LineageSource> sources;
        private final AuditTrail audit;

        public Open(QuarantineRepository quarantines, List<LineageSource> sources, AuditTrail audit) {
            this.quarantines = Objects.requireNonNull(quarantines);
            this.sources = List.copyOf(Objects.requireNonNull(sources));
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public Quarantine handle(UUID actorId, UUID breweryId, NodeType type, UUID nodeId, String reason) {
            // O nó precisa existir: quarentenar um id digitado errado bloquearia nada e daria a
            // impressão de que a contenção está de pé.
            var origin = FederatedLineageGraph.describe(sources, breweryId, type, nodeId)
                    .orElseThrow(() -> new UnknownNodeException(type, nodeId));
            quarantines.findOpenFor(breweryId, type, nodeId).ifPresent(existing -> {
                throw new AlreadyQuarantinedException(existing.id());
            });

            var quarantine = Quarantine.open(breweryId, origin, reason, actorId, Instant.now());
            quarantines.insert(quarantine);

            audit.record(AuditEvent.success(breweryId, actorId, "traceability.quarantine.open",
                    "traceability.quarantine", quarantine.id().toString(),
                    Map.of("nodeType", type.name(), "nodeId", nodeId.toString(),
                            "origin", quarantine.originLabel() == null ? "" : quarantine.originLabel(),
                            "reason", quarantine.reason())));
            return quarantine;
        }
    }

    public static final class Release implements QuarantineCommands.Release {

        private final QuarantineRepository quarantines;
        private final AuditTrail audit;

        public Release(QuarantineRepository quarantines, AuditTrail audit) {
            this.quarantines = Objects.requireNonNull(quarantines);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public void handle(UUID actorId, UUID breweryId, UUID quarantineId, String justification) {
            var quarantine = quarantines.findForUpdate(breweryId, quarantineId)
                    .orElseThrow(() -> new UnknownQuarantineException(quarantineId));
            var version = quarantine.version();
            quarantine.release(actorId, justification, Instant.now());
            if (!quarantines.updateStatus(quarantine, version)) {
                throw new IllegalStateException("quarentena alterada por outra operação; tente novamente");
            }

            audit.record(AuditEvent.success(breweryId, actorId, "traceability.quarantine.release",
                    "traceability.quarantine", quarantine.id().toString(),
                    Map.of("nodeType", quarantine.nodeType().name(),
                            "nodeId", quarantine.nodeId().toString(),
                            "justification", quarantine.releaseJustification())));
        }
    }
}
