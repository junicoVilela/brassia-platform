package br.com.brew.brassia.sensory.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.production.BatchLookup;
import br.com.brew.brassia.sensory.application.port.inbound.SessionCommands;
import br.com.brew.brassia.sensory.application.port.outbound.SensoryPolicyRepository;
import br.com.brew.brassia.sensory.application.port.outbound.SensorySessionRepository;
import br.com.brew.brassia.sensory.domain.AlreadyEvaluatedException;
import br.com.brew.brassia.sensory.domain.SensoryAttribute;
import br.com.brew.brassia.sensory.domain.SensoryEvaluation;
import br.com.brew.brassia.sensory.domain.SensorySession;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Ciclo da sessão sensorial (SEN-001).
 *
 * <p>A auditoria registra o que aconteceu <strong>sem revelar nota</strong> enquanto a sessão está
 * aberta: gravar a nota no evento de auditoria seria uma porta lateral para o resultado sair antes
 * do fechamento.
 */
public final class SessionHandlers {

    private SessionHandlers() {
    }

    private static SensorySession require(SensorySessionRepository sessions, UUID breweryId, UUID id) {
        return sessions.lockById(breweryId, id)
                .orElseThrow(() -> new IllegalArgumentException("sessão sensorial inexistente"));
    }

    public static final class Create implements SessionCommands.Create {

        private final SensorySessionRepository sessions;
        private final SensoryPolicyRepository policies;
        private final AuditTrail audit;

        public Create(SensorySessionRepository sessions, SensoryPolicyRepository policies,
                AuditTrail audit) {
            this.sessions = Objects.requireNonNull(sessions);
            this.policies = Objects.requireNonNull(policies);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public UUID handle(Command command) {
            if (sessions.existsByCode(command.breweryId(), command.code())) {
                throw new IllegalStateException("já existe sessão com o código " + command.code());
            }
            // A escala vem do parâmetro da cervejaria e fica congelada na sessão.
            var maxScore = policies.find(command.breweryId()).maxScore();
            var session = SensorySession.draft(command.breweryId(), command.code(), command.purpose(),
                    command.scheduledFor(), maxScore);
            sessions.insert(session);

            audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "sensory.session.create",
                    "sensory.session", session.id().toString(),
                    Map.of("code", session.code(), "scheduledFor", session.scheduledFor().toString(),
                            "maxScore", String.valueOf(session.maxScore()))));
            return session.id();
        }
    }

    public static final class Amend implements SessionCommands.Amend {

        private final SensorySessionRepository sessions;
        private final AuditTrail audit;

        public Amend(SensorySessionRepository sessions, AuditTrail audit) {
            this.sessions = Objects.requireNonNull(sessions);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public void handle(Command command) {
            var session = require(sessions, command.breweryId(), command.sessionId());
            session.amend(command.purpose(), command.scheduledFor());
            sessions.update(session);

            audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "sensory.session.amend",
                    "sensory.session", session.id().toString(), Map.of("code", session.code())));
        }
    }

    public static final class AddSample implements SessionCommands.AddSample {

        private final SensorySessionRepository sessions;
        private final BatchLookup batches;
        private final AuditTrail audit;

        public AddSample(SensorySessionRepository sessions, BatchLookup batches, AuditTrail audit) {
            this.sessions = Objects.requireNonNull(sessions);
            this.batches = Objects.requireNonNull(batches);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public UUID handle(Command command) {
            var session = require(sessions, command.breweryId(), command.sessionId());
            if (!batches.exists(command.breweryId(), command.batchId())) {
                throw new IllegalArgumentException("lote inexistente");
            }
            var sample = session.addSample(command.batchId(), command.note());
            sessions.update(session);

            // O código cego não entra na auditoria junto do lote: o par revelaria a amostra.
            audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "sensory.sample.add",
                    "sensory.session", session.id().toString(),
                    Map.of("code", session.code(), "samples", String.valueOf(session.samples().size()))));
            return sample.id();
        }
    }

    public static final class RemoveSample implements SessionCommands.RemoveSample {

        private final SensorySessionRepository sessions;
        private final AuditTrail audit;

        public RemoveSample(SensorySessionRepository sessions, AuditTrail audit) {
            this.sessions = Objects.requireNonNull(sessions);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public void handle(Command command) {
            var session = require(sessions, command.breweryId(), command.sessionId());
            session.removeSample(command.sampleId());
            sessions.update(session);

            audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "sensory.sample.remove",
                    "sensory.session", session.id().toString(), Map.of("code", session.code())));
        }
    }

    public static final class Open implements SessionCommands.Open {

        private final SensorySessionRepository sessions;
        private final AuditTrail audit;

        public Open(SensorySessionRepository sessions, AuditTrail audit) {
            this.sessions = Objects.requireNonNull(sessions);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public void handle(Command command) {
            var session = require(sessions, command.breweryId(), command.sessionId());
            session.open(Instant.now());
            sessions.update(session);

            audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "sensory.session.open",
                    "sensory.session", session.id().toString(),
                    Map.of("code", session.code(), "samples", String.valueOf(session.samples().size()))));
        }
    }

    public static final class Close implements SessionCommands.Close {

        private final SensorySessionRepository sessions;
        private final AuditTrail audit;

        public Close(SensorySessionRepository sessions, AuditTrail audit) {
            this.sessions = Objects.requireNonNull(sessions);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public void handle(Command command) {
            var session = require(sessions, command.breweryId(), command.sessionId());
            session.close(Instant.now());
            sessions.update(session);

            audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "sensory.session.close",
                    "sensory.session", session.id().toString(),
                    Map.of("code", session.code(),
                            "evaluations", String.valueOf(
                                    sessions.countEvaluations(command.breweryId(), session.id())))));
        }
    }

    public static final class SubmitEvaluation implements SessionCommands.SubmitEvaluation {

        private final SensorySessionRepository sessions;
        private final AuditTrail audit;

        public SubmitEvaluation(SensorySessionRepository sessions, AuditTrail audit) {
            this.sessions = Objects.requireNonNull(sessions);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public UUID handle(Command command) {
            var session = sessions.findById(command.breweryId(), command.sessionId())
                    .orElseThrow(() -> new IllegalArgumentException("sessão sensorial inexistente"));
            session.requireAcceptingEvaluations();
            var sample = session.sample(command.sampleId())
                    .orElseThrow(() -> new IllegalArgumentException("amostra inexistente na sessão"));
            if (sessions.hasEvaluated(command.breweryId(), sample.id(), command.actorId())) {
                throw new AlreadyEvaluatedException(sample.blindCode().value());
            }

            var scores = new EnumMap<SensoryAttribute, Integer>(SensoryAttribute.class);
            command.scores().forEach((key, value) -> scores.put(SensoryAttribute.valueOf(key), value));

            var evaluation = SensoryEvaluation.submit(command.breweryId(), session.id(), sample.id(),
                    command.actorId(), scores, command.descriptors() == null ? java.util.List.of()
                            : command.descriptors(),
                    command.note(), Instant.now(), session.maxScore());
            sessions.insertEvaluation(evaluation);

            // Sem nota e sem lote no evento: a auditoria não pode ser a fresta por onde o
            // resultado escapa antes do fechamento.
            audit.record(AuditEvent.success(command.breweryId(), command.actorId(),
                    "sensory.evaluation.submit", "sensory.session", session.id().toString(),
                    Map.of("code", session.code(), "blindCode", sample.blindCode().value())));
            return evaluation.id();
        }
    }
}
