package br.com.brew.brassia.quality.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.quality.application.port.inbound.NonConformityCommands;
import br.com.brew.brassia.quality.application.port.outbound.MeasurementRepository;
import br.com.brew.brassia.quality.application.port.outbound.CapaPolicyRepository;
import br.com.brew.brassia.quality.application.port.outbound.NonConformityRepository;
import br.com.brew.brassia.quality.domain.CapaActionKind;
import br.com.brew.brassia.quality.domain.CapaPolicy;
import br.com.brew.brassia.quality.domain.NonConformity;
import br.com.brew.brassia.quality.domain.NonConformitySource;
import br.com.brew.brassia.quality.domain.Severity;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Tratamento da não conformidade (QLT-002): conter, investigar, agir, verificar e encerrar.
 *
 * <p>Cada comando é auditado com o estado resultante, porque é essa cadeia que uma auditoria
 * externa vai percorrer para saber se o problema foi realmente tratado — e quando.
 */
public final class NonConformityHandlers {

    private NonConformityHandlers() {
    }

    /** Sem política e sem prazo informado, o comando é recusado — nada é inventado. */
    private static java.time.LocalDate requireDerived(CapaPolicy.Dates derived,
            java.util.function.Function<CapaPolicy.Dates, java.time.LocalDate> field) {
        if (derived == null) {
            throw new IllegalArgumentException(
                    "informe os prazos ou configure a política de CAPA para esta severidade");
        }
        return field.apply(derived);
    }

    private static NonConformity require(NonConformityRepository repo, UUID breweryId, UUID id) {
        return repo.lockById(breweryId, id)
                .orElseThrow(() -> new IllegalArgumentException("não conformidade inexistente"));
    }

    /**
     * NC-AAAA-NNNN, numerada por cervejaria e ano.
     *
     * <p>Por ano porque é assim que se referencia não conformidade numa auditoria: "a NC-2026-0007" diz
     * quando aconteceu. Um identificador aleatório seria ilegível em voz alta, que é onde ele mais é usado.
     */
    private static String nextCode(NonConformityRepository repository, UUID breweryId) {
        var year = LocalDate.now(ZoneOffset.UTC).getYear();
        return "NC-%d-%04d".formatted(year, repository.nextSequence(breweryId, year));
    }

    public static final class Open implements NonConformityCommands.Open {

        private final NonConformityRepository nonConformities;
        private final MeasurementRepository measurements;
        private final CapaPolicyRepository policies;
        private final AuditTrail audit;

        public Open(NonConformityRepository nonConformities, MeasurementRepository measurements,
                CapaPolicyRepository policies, AuditTrail audit) {
            this.nonConformities = Objects.requireNonNull(nonConformities);
            this.measurements = Objects.requireNonNull(measurements);
            this.policies = Objects.requireNonNull(policies);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public UUID handle(Command command) {
            var code = command.code() == null || command.code().isBlank()
                    ? nextCode(nonConformities, command.breweryId())
                    : command.code();
            if (nonConformities.existsByCode(command.breweryId(), code)) {
                throw new IllegalStateException("já existe não conformidade com o código " + code);
            }
            // Lote inexistente viraria uma NC afirmando falar de um lote que não existe. Quem garante é
            // a CHAVE ESTRANGEIRA, e não uma consulta a `production`: a checagem prévia daria a este
            // módulo uma dependência de produção que fecharia o ciclo
            // production → traceability → quality → production. O banco recusa, e o handler traduz.
            // Desvio inexistente viraria uma NC apontando para o nada, e o encerramento não teria o
            // que fechar.
            if (command.deviationId() != null
                    && measurements.findDeviation(command.breweryId(), command.deviationId()).isEmpty()) {
                throw new IllegalArgumentException("desvio inexistente");
            }
            var severity = Severity.valueOf(command.severity());
            // Prazos informados sempre vencem; a política (PRM-001) preenche os três de uma vez
            // quando nenhum veio, porque prazo pela metade não descreve tratamento nenhum.
            var today = LocalDate.now(ZoneOffset.UTC);
            var derived = command.containmentDueOn() == null && command.investigationDueOn() == null
                    && command.verificationDueOn() == null
                            ? policies.find(command.breweryId()).datesFor(severity, today).orElse(null)
                            : null;
            var containment = command.containmentDueOn() != null ? command.containmentDueOn()
                    : requireDerived(derived, CapaPolicy.Dates::containmentDueOn);
            var investigation = command.investigationDueOn() != null ? command.investigationDueOn()
                    : requireDerived(derived, CapaPolicy.Dates::investigationDueOn);
            var verification = command.verificationDueOn() != null ? command.verificationDueOn()
                    : requireDerived(derived, CapaPolicy.Dates::verificationDueOn);

            var nc = NonConformity.open(command.breweryId(), code, command.title(),
                    command.description(), NonConformitySource.valueOf(command.source()),
                    command.deviationId(), command.batchId(), severity, containment, investigation,
                    verification, Instant.now(), command.actorId());
            nonConformities.insert(nc);

            audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "quality.nc.open",
                    "quality.non_conformity", nc.id().toString(),
                    Map.of("code", nc.code(), "source", nc.source().name(),
                            "severity", nc.severity().name(),
                            "batchId", nc.batchId().map(UUID::toString).orElse(""))));
            return nc.id();
        }
    }

    public static final class Contain implements NonConformityCommands.Contain {

        private final NonConformityRepository nonConformities;
        private final AuditTrail audit;

        public Contain(NonConformityRepository nonConformities, AuditTrail audit) {
            this.nonConformities = Objects.requireNonNull(nonConformities);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public void handle(Command command) {
            var nc = require(nonConformities, command.breweryId(), command.nonConformityId());
            nc.contain(command.description(), Instant.now(), command.actorId());
            nonConformities.update(nc);

            audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "quality.nc.contain",
                    "quality.non_conformity", nc.id().toString(),
                    Map.of("code", nc.code(), "status", nc.status().name())));
        }
    }

    public static final class Investigate implements NonConformityCommands.Investigate {

        private final NonConformityRepository nonConformities;
        private final AuditTrail audit;

        public Investigate(NonConformityRepository nonConformities, AuditTrail audit) {
            this.nonConformities = Objects.requireNonNull(nonConformities);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public void handle(Command command) {
            var nc = require(nonConformities, command.breweryId(), command.nonConformityId());
            nc.investigate(command.rootCause(), command.method(), Instant.now(), command.actorId());
            nonConformities.update(nc);

            audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "quality.nc.investigate",
                    "quality.non_conformity", nc.id().toString(),
                    Map.of("code", nc.code(), "method", command.method())));
        }
    }

    public static final class PlanAction implements NonConformityCommands.PlanAction {

        private final NonConformityRepository nonConformities;
        private final AuditTrail audit;

        public PlanAction(NonConformityRepository nonConformities, AuditTrail audit) {
            this.nonConformities = Objects.requireNonNull(nonConformities);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public UUID handle(Command command) {
            var nc = require(nonConformities, command.breweryId(), command.nonConformityId());
            var action = nc.planAction(CapaActionKind.valueOf(command.kind()), command.description(),
                    command.owner(), command.dueOn());
            nonConformities.update(nc);

            audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "quality.nc.plan-action",
                    "quality.non_conformity", nc.id().toString(),
                    Map.of("code", nc.code(), "kind", action.kind().name(),
                            "dueOn", action.dueOn().toString())));
            return action.id();
        }
    }

    public static final class CompleteAction implements NonConformityCommands.CompleteAction {

        private final NonConformityRepository nonConformities;
        private final AuditTrail audit;

        public CompleteAction(NonConformityRepository nonConformities, AuditTrail audit) {
            this.nonConformities = Objects.requireNonNull(nonConformities);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public void handle(Command command) {
            var nc = require(nonConformities, command.breweryId(), command.nonConformityId());
            nc.completeAction(command.actionId(), Instant.now());
            nonConformities.update(nc);

            audit.record(AuditEvent.success(command.breweryId(), command.actorId(),
                    "quality.nc.complete-action", "quality.non_conformity", nc.id().toString(),
                    Map.of("code", nc.code(), "action", command.actionId().toString())));
        }
    }

    public static final class Verify implements NonConformityCommands.Verify {

        private final NonConformityRepository nonConformities;
        private final AuditTrail audit;

        public Verify(NonConformityRepository nonConformities, AuditTrail audit) {
            this.nonConformities = Objects.requireNonNull(nonConformities);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public void handle(Command command) {
            var nc = require(nonConformities, command.breweryId(), command.nonConformityId());
            nc.verify(command.effective(), command.evidence(), Instant.now(), command.actorId());
            nonConformities.update(nc);

            audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "quality.nc.verify",
                    "quality.non_conformity", nc.id().toString(),
                    Map.of("code", nc.code(), "effective", String.valueOf(command.effective()),
                            "status", nc.status().name())));
        }
    }

    /**
     * Encerra a NC e, com ela, o desvio que a originou — é o ciclo aberto na QLT-001 se fechando.
     * As duas gravações vão no mesmo commit: um desvio fechado sem NC encerrada, ou o contrário,
     * seria um painel mentindo em uma das duas telas.
     */
    public static final class Close implements NonConformityCommands.Close {

        private final NonConformityRepository nonConformities;
        private final MeasurementRepository measurements;
        private final AuditTrail audit;

        public Close(NonConformityRepository nonConformities, MeasurementRepository measurements,
                AuditTrail audit) {
            this.nonConformities = Objects.requireNonNull(nonConformities);
            this.measurements = Objects.requireNonNull(measurements);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public void handle(Command command) {
            var nc = require(nonConformities, command.breweryId(), command.nonConformityId());
            nc.close(Instant.now(), command.actorId());
            nonConformities.update(nc);

            nc.deviationToClose()
                    .flatMap(id -> measurements.findDeviation(command.breweryId(), id))
                    .ifPresent(deviation -> {
                        deviation.close();
                        measurements.updateDeviation(deviation);
                    });

            audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "quality.nc.close",
                    "quality.non_conformity", nc.id().toString(),
                    Map.of("code", nc.code(),
                            "deviation", nc.deviationId().map(UUID::toString).orElse("-"))));
        }
    }
}
