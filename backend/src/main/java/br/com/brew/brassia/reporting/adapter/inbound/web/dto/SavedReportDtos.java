package br.com.brew.brassia.reporting.adapter.inbound.web.dto;

import br.com.brew.brassia.reporting.domain.ReportRun;
import br.com.brew.brassia.reporting.domain.SavedReport;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Contratos dos relatórios salvos (RPT-003). */
public final class SavedReportDtos {

    private SavedReportDtos() {
    }

    /**
     * @param ownerUserId o proprietário técnico: é a alçada dele, resolvida a cada execução, que
     *                    decide se o relatório sai
     * @param recipients  ids de usuários da plataforma. Não há campo de e-mail livre, e é
     *                    deliberado: só de usuário se sabe a alçada
     */
    public record DefineRequest(@NotBlank @Size(max = 120) String name,
            @NotNull SavedReport.ReportKind kind, Map<String, String> filters,
            @NotBlank String timezone, @NotNull SavedReport.ReportFormat format,
            @NotNull SavedReport.Schedule schedule,
            @Min(1) @Max(3650) int retentionDays, @NotNull UUID ownerUserId, Set<UUID> recipients) {}

    public record RedefineRequest(Map<String, String> filters, @NotBlank String timezone,
            @NotNull SavedReport.Schedule schedule, @Min(1) @Max(3650) int retentionDays,
            Set<UUID> recipients) {}

    public record ActivateRequest(@NotNull Boolean active) {}

    public record DeliverRequest(@NotNull UUID recipientId, @NotNull Boolean delivered,
            @Size(max = 500) String detail) {}

    public record SavedReportView(UUID id, String name, String kind, int definitionVersion,
            Map<String, String> filters, String timezone, String format, String schedule,
            int retentionDays, UUID ownerUserId, List<UUID> recipients, boolean active,
            Instant createdAt) {

        public static SavedReportView from(SavedReport report) {
            return new SavedReportView(report.id(), report.name(), report.kind().name(),
                    report.definitionVersion(), report.filters(), report.timezone().getId(),
                    report.format().name(), report.schedule().name(), report.retentionDays(),
                    report.ownerUserId(), report.recipients().stream().sorted().toList(),
                    report.active(), report.createdAt());
        }

        public static List<SavedReportView> from(List<SavedReport> reports) {
            return reports.stream().map(SavedReportView::from).toList();
        }
    }

    /**
     * @param refusalReason por que não rodou. O caso que importa: o dono perdeu a alçada
     * @param downloadToken só vem quando quem pediu é destinatário ou dono — o link é pessoal
     */
    public record ReportRunView(UUID id, UUID reportId, int definitionVersion, String status,
            String refusalReason, Instant periodFrom, Instant periodTo, Instant expiresAt,
            Instant executedAt, boolean expired, List<DeliveryView> deliveries,
            String downloadToken) {

        public static ReportRunView from(ReportRun run, Instant now, String downloadToken) {
            return new ReportRunView(run.id(), run.reportId(), run.definitionVersion(),
                    run.status().name(), run.refusalReason(), run.periodFrom(), run.periodTo(),
                    run.expiresAt(), run.executedAt(), run.expired(now),
                    run.deliveryList().stream().map(DeliveryView::from).toList(), downloadToken);
        }

        public static List<ReportRunView> from(List<ReportRun> runs, Instant now) {
            return runs.stream().map(run -> from(run, now, null)).toList();
        }
    }

    public record DeliveryView(UUID userId, String status, String detail, int attempts,
            Instant lastAttemptAt) {

        static DeliveryView from(ReportRun.Delivery delivery) {
            return new DeliveryView(delivery.userId(), delivery.status().name(), delivery.detail(),
                    delivery.attempts(), delivery.lastAttemptAt());
        }
    }
}
