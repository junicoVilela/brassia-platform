package br.com.brew.brassia.reporting.application.service;

import br.com.brew.brassia.reporting.application.port.inbound.BatchReportQueries;
import br.com.brew.brassia.reporting.application.port.inbound.DashboardQueries;
import br.com.brew.brassia.reporting.application.port.outbound.SavedReportRepository;
import br.com.brew.brassia.reporting.domain.ReportRun;
import br.com.brew.brassia.reporting.domain.SavedReport;
import br.com.brew.brassia.security.EffectivePermissionLookup;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Produz uma execução de relatório salvo (RPT-003).
 *
 * <p><strong>A alçada é a do proprietário técnico, resolvida agora.</strong> Não a de quem apertou
 * o botão, não a que ele tinha quando salvou a definição, e nunca um privilégio de sistema. Um
 * relatório programado que rodasse como sistema entregaria, todo mês, dados que ninguém autoriza a
 * entregar — e continuaria entregando depois de o dono sair da empresa.
 *
 * <p>Dono sem alçada não faz a execução falhar: faz a execução <strong>recusar</strong>, com o
 * motivo registrado. Falhar em silêncio deixaria a fábrica achando que o relatório continua indo.
 */
public final class ReportExecutionService {

    private final SavedReportRepository reports;
    private final DashboardQueries dashboard;
    private final BatchReportQueries batchReports;
    private final EffectivePermissionLookup permissions;
    private final ObjectMapper json;

    public ReportExecutionService(SavedReportRepository reports, DashboardQueries dashboard,
            BatchReportQueries batchReports, EffectivePermissionLookup permissions, ObjectMapper json) {
        this.reports = Objects.requireNonNull(reports);
        this.dashboard = Objects.requireNonNull(dashboard);
        this.batchReports = Objects.requireNonNull(batchReports);
        this.permissions = Objects.requireNonNull(permissions);
        this.json = Objects.requireNonNull(json);
    }

    /**
     * Executa a definição para a chave informada.
     *
     * <p>Chave repetida devolve a execução que já existe, sem refazer nada. É o que impede o
     * agendador de produzir dois artefatos do mesmo período quando ele roda duas vezes.
     */
    public ReportRun execute(SavedReport report, String idempotencyKey, Instant at) {
        var existing = reports.findRunByKey(report.id(), idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        var effective = permissions.permissionsOf(report.ownerUserId(), report.breweryId());
        if (!effective.contains(report.requiredPermission())) {
            return reports.saveRun(ReportRun.refused(report, idempotencyKey,
                    "o proprietário técnico não tem mais a permissão " + report.requiredPermission()
                            + " nesta cervejaria: o relatório não é gerado com privilégio de sistema",
                    at));
        }

        var period = periodOf(report, at);
        var content = render(report, period);
        return reports.saveRun(ReportRun.succeeded(report, idempotencyKey, content, period.from(),
                period.to(), at, report.recipients()));
    }

    /**
     * A chave de um período programado.
     *
     * <p>Sai do calendário <strong>no fuso da definição</strong>: "o relatório de 7 de agosto" é o
     * dia 7 na fábrica, e um agendador em UTC criaria dois artefatos para o mesmo dia em quem opera
     * a oeste de Greenwich.
     */
    public static String scheduledKey(SavedReport report, Instant at) {
        var local = LocalDate.ofInstant(at, report.timezone());
        return switch (report.schedule()) {
            case DAILY -> "DAILY-" + local;
            case WEEKLY -> "WEEKLY-" + local.get(WeekFields.ISO.weekBasedYear()) + "-W"
                    + local.get(WeekFields.ISO.weekOfWeekBasedYear());
            case MONTHLY -> "MONTHLY-" + local.getYear() + "-"
                    + String.format(Locale.ROOT, "%02d", local.getMonthValue());
            case MANUAL -> throw new IllegalArgumentException("definição manual não tem chave de período");
        };
    }

    private Period periodOf(SavedReport report, Instant at) {
        var days = switch (report.schedule()) {
            case DAILY -> 1;
            case WEEKLY -> 7;
            // Trinta dias e não "mês civil": o mês civil exigiria decidir se o relatório do dia 3
            // cobre o mês anterior inteiro, e ninguém decidiu isso ainda.
            case MONTHLY, MANUAL -> 30;
        };
        return new Period(at.minus(days, ChronoUnit.DAYS), at);
    }

    private String render(SavedReport report, Period period) {
        try {
            return switch (report.kind()) {
                case DASHBOARD -> json.writeValueAsString(
                        dashboard.dashboard(report.breweryId(), period.from(), period.to()));
                case BATCH_REPORT -> json.writeValueAsString(
                        batchReports.ofBatch(report.breweryId(), batchIdOf(report)));
            };
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("não foi possível serializar o relatório", ex);
        }
    }

    private static UUID batchIdOf(SavedReport report) {
        var value = report.filters().get("batchId");
        if (value == null) {
            throw new IllegalArgumentException("relatório de lote exige o filtro batchId");
        }
        return UUID.fromString(value);
    }

    private record Period(Instant from, Instant to) {}
}
