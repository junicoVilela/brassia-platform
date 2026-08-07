package br.com.brew.brassia.reporting.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.reporting.application.port.inbound.SavedReportUseCases;
import br.com.brew.brassia.reporting.application.port.outbound.SavedReportRepository;
import br.com.brew.brassia.reporting.domain.ReportRun;
import br.com.brew.brassia.reporting.domain.SavedReport;
import br.com.brew.brassia.reporting.domain.UnknownSavedReportException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Definição, execução e entrega dos relatórios salvos (RPT-003). */
public final class SavedReportHandlers {

    private SavedReportHandlers() {
    }

    public static final class Queries implements SavedReportUseCases.Queries {

        private final SavedReportRepository reports;

        public Queries(SavedReportRepository reports) {
            this.reports = Objects.requireNonNull(reports);
        }

        @Override
        public List<SavedReport> findAll(UUID breweryId) {
            return reports.findAll(breweryId);
        }

        @Override
        public SavedReport ofId(UUID breweryId, UUID reportId) {
            return reports.findById(breweryId, reportId)
                    .orElseThrow(() -> new UnknownSavedReportException(reportId));
        }

        @Override
        public List<ReportRun> runsOf(UUID breweryId, UUID reportId) {
            ofIdOrThrow(reports, breweryId, reportId);
            return reports.findRuns(breweryId, reportId);
        }
    }

    public static final class Define implements SavedReportUseCases.Define {

        private final SavedReportRepository reports;
        private final AuditTrail audit;

        public Define(SavedReportRepository reports, AuditTrail audit) {
            this.reports = Objects.requireNonNull(reports);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public SavedReport handle(UUID actorId, UUID breweryId, Command command) {
            var report = SavedReport.define(breweryId, command.name(), command.kind(),
                    command.filters(), command.timezone(), command.format(), command.schedule(),
                    command.retentionDays(), command.ownerUserId(), command.recipients(),
                    Instant.now(), actorId);
            reports.save(report);
            audit.record(AuditEvent.success(breweryId, actorId, "reporting.saved.define",
                    "reporting.saved_report", report.id().toString(),
                    Map.of("name", report.name(), "kind", report.kind().name(),
                            "schedule", report.schedule().name(),
                            "owner", report.ownerUserId().toString(),
                            "recipients", String.valueOf(report.recipients().size()))));
            return report;
        }
    }

    public static final class Redefine implements SavedReportUseCases.Redefine {

        private final SavedReportRepository reports;
        private final AuditTrail audit;

        public Redefine(SavedReportRepository reports, AuditTrail audit) {
            this.reports = Objects.requireNonNull(reports);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public SavedReport handle(UUID actorId, UUID breweryId, UUID reportId, Command command) {
            var current = ofIdOrThrow(reports, breweryId, reportId);
            var updated = current.redefine(command.filters(), command.timezone(), command.schedule(),
                    command.retentionDays(), command.recipients());
            reports.update(updated);
            // A versão nova vai na auditoria: é por ela que se liga uma execução antiga ao recorte
            // que existia quando ela rodou.
            audit.record(AuditEvent.success(breweryId, actorId, "reporting.saved.redefine",
                    "reporting.saved_report", reportId.toString(),
                    Map.of("version", String.valueOf(updated.definitionVersion()))));
            return updated;
        }
    }

    public static final class Activate implements SavedReportUseCases.Activate {

        private final SavedReportRepository reports;
        private final AuditTrail audit;

        public Activate(SavedReportRepository reports, AuditTrail audit) {
            this.reports = Objects.requireNonNull(reports);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public SavedReport handle(UUID actorId, UUID breweryId, UUID reportId, boolean active) {
            var updated = ofIdOrThrow(reports, breweryId, reportId).activate(active);
            reports.update(updated);
            audit.record(AuditEvent.success(breweryId, actorId, "reporting.saved.activate",
                    "reporting.saved_report", reportId.toString(),
                    Map.of("active", String.valueOf(active))));
            return updated;
        }
    }

    /**
     * Executa agora.
     *
     * <p>Quem pede não empresta a própria alçada: mesmo um administrador disparando o relatório de
     * outra pessoa recebe o que a alçada <em>daquela pessoa</em> permite. Fosse o contrário, pedir
     * a execução seria uma forma de ler o que não se pode ler.
     */
    public static final class Run implements SavedReportUseCases.Run {

        private final SavedReportRepository reports;
        private final ReportExecutionService execution;
        private final AuditTrail audit;

        public Run(SavedReportRepository reports, ReportExecutionService execution, AuditTrail audit) {
            this.reports = Objects.requireNonNull(reports);
            this.execution = Objects.requireNonNull(execution);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public ReportRun handle(UUID actorId, UUID breweryId, UUID reportId) {
            var report = ofIdOrThrow(reports, breweryId, reportId);
            var run = execution.execute(report, "MANUAL-" + UUID.randomUUID(), Instant.now());
            audit.record(AuditEvent.success(breweryId, actorId, "reporting.saved.run",
                    "reporting.report_run", run.id().toString(),
                    Map.of("report", reportId.toString(), "status", run.status().name(),
                            "owner", report.ownerUserId().toString())));
            return run;
        }
    }

    public static final class Deliver implements SavedReportUseCases.Deliver {

        private final SavedReportRepository reports;
        private final AuditTrail audit;

        public Deliver(SavedReportRepository reports, AuditTrail audit) {
            this.reports = Objects.requireNonNull(reports);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public ReportRun handle(UUID actorId, UUID breweryId, UUID runId, UUID recipientId,
                boolean delivered, String detail) {
            var run = reports.findRun(breweryId, runId)
                    .orElseThrow(() -> new UnknownSavedReportException(runId));
            var status = delivered ? ReportRun.Delivery.Status.DELIVERED
                    : ReportRun.Delivery.Status.REFUSED;
            // Reentregar não refaz o relatório: o artefato já existe, e só a linha de entrega muda.
            var updated = run.deliver(recipientId, status, detail, Instant.now());
            reports.updateDeliveries(updated);
            audit.record(AuditEvent.success(breweryId, actorId, "reporting.saved.deliver",
                    "reporting.report_run", runId.toString(),
                    Map.of("recipient", recipientId.toString(), "status", status.name())));
            return updated;
        }
    }

    /** Abre o link temporário, ou recusa quando o prazo passou. Cada abertura fica auditada. */
    public static final class Download implements SavedReportUseCases.Download {

        private final SavedReportRepository reports;
        private final AuditTrail audit;

        public Download(SavedReportRepository reports, AuditTrail audit) {
            this.reports = Objects.requireNonNull(reports);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public Optional<Granted> handle(String token, UUID actorId) {
            var grant = reports.findToken(token).orElse(null);
            if (grant == null) {
                return Optional.empty();
            }
            var now = Instant.now();
            if (grant.expired(now) || !grant.userId().equals(actorId)) {
                // Link vencido e link de outra pessoa recebem a mesma resposta: dizer qual dos dois
                // foi ensinaria a diferença a quem está testando tokens.
                audit.record(AuditEvent.success(grant.breweryId(), actorId,
                        "reporting.saved.download-refused", "reporting.report_run",
                        grant.runId().toString(), Map.of("reason",
                                grant.expired(now) ? "expired" : "wrong_user")));
                return Optional.empty();
            }
            var run = reports.findRun(grant.breweryId(), grant.runId()).orElse(null);
            if (run == null || !run.succeeded() || run.expired(now)) {
                return Optional.empty();
            }
            var report = reports.findById(grant.breweryId(), run.reportId()).orElse(null);
            audit.record(AuditEvent.success(grant.breweryId(), actorId, "reporting.saved.download",
                    "reporting.report_run", run.id().toString(),
                    Map.of("report", run.reportId().toString())));
            return Optional.of(new Granted(run, report == null ? "relatorio" : report.name()));
        }
    }

    private static SavedReport ofIdOrThrow(SavedReportRepository reports, UUID breweryId,
            UUID reportId) {
        return reports.findById(breweryId, reportId)
                .orElseThrow(() -> new UnknownSavedReportException(reportId));
    }
}
