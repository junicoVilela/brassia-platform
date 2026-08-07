package br.com.brew.brassia.reporting.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.brew.brassia.reporting.application.port.inbound.BatchReportQueries;
import br.com.brew.brassia.reporting.application.port.inbound.DashboardQueries;
import br.com.brew.brassia.reporting.application.port.outbound.SavedReportRepository;
import br.com.brew.brassia.reporting.domain.ReportRun;
import br.com.brew.brassia.reporting.domain.SavedReport;
import br.com.brew.brassia.reporting.domain.SavedReport.ReportFormat;
import br.com.brew.brassia.reporting.domain.SavedReport.ReportKind;
import br.com.brew.brassia.reporting.domain.SavedReport.Schedule;
import br.com.brew.brassia.security.EffectivePermissionLookup;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A regra que a história chama de "autorização efetiva do proprietário técnico" (RPT-003).
 *
 * <p>É o teste mais importante desta história: sem ele, a diferença entre "roda com a alçada do
 * dono" e "roda como sistema" é invisível — as duas produzem o mesmo arquivo enquanto o dono ainda
 * tem a permissão.
 */
class ReportExecutionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-07T12:00:00Z");
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final UUID OWNER = UUID.randomUUID();

    @Test
    @DisplayName("com o dono autorizado, a execução produz o artefato")
    void donoAutorizadoProduz() {
        var repository = new InMemoryRepository();
        var service = service(repository, Set.of("reporting.dashboard.read"));

        var run = service.execute(report(Schedule.DAILY), "DAILY-2026-08-07", NOW);

        assertThat(run.succeeded()).isTrue();
        assertThat(run.content()).isNotBlank();
    }

    @Test
    @DisplayName("dono sem a permissão recusa a execução com motivo, e não gera nada")
    void donoSemPermissaoRecusa() {
        var repository = new InMemoryRepository();
        // O dono foi desligado, ou perdeu o grupo: as permissões de agora não têm mais a do painel.
        var service = service(repository, Set.of("reporting.batch.read"));

        var run = service.execute(report(Schedule.DAILY), "DAILY-2026-08-07", NOW);

        assertThat(run.succeeded()).isFalse();
        assertThat(run.content()).isNull();
        assertThat(run.refusalReason()).contains("reporting.dashboard.read");
        assertThat(run.refusalReason()).contains("privilégio de sistema");
    }

    @Test
    @DisplayName("dono sem alçada nenhuma também recusa — não há execução como sistema")
    void semAlcadaNenhumaRecusa() {
        var repository = new InMemoryRepository();
        var service = service(repository, Set.of());

        assertThat(service.execute(report(Schedule.DAILY), "K", NOW).succeeded()).isFalse();
    }

    @Test
    @DisplayName("a mesma chave devolve a execução existente, sem produzir de novo")
    void chaveRepetidaNaoProduzDeNovo() {
        var repository = new InMemoryRepository();
        var service = service(repository, Set.of("reporting.dashboard.read"));
        var report = report(Schedule.DAILY);

        var first = service.execute(report, "DAILY-2026-08-07", NOW);
        var second = service.execute(report, "DAILY-2026-08-07", NOW.plusSeconds(3600));

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(repository.runs).hasSize(1);
    }

    @Test
    @DisplayName("a chave do período sai do calendário no fuso da definição, não em UTC")
    void aChaveUsaOFusoDaDefinicao() {
        // 02:00Z de 8 de agosto ainda é dia 7 em São Paulo: o relatório "de 7 de agosto" é um só.
        var madrugada = Instant.parse("2026-08-08T02:00:00Z");

        assertThat(ReportExecutionService.scheduledKey(report(Schedule.DAILY), madrugada))
                .isEqualTo("DAILY-2026-08-07");
        assertThat(ReportExecutionService.scheduledKey(report(Schedule.MONTHLY), madrugada))
                .isEqualTo("MONTHLY-2026-08");
    }

    @Test
    @DisplayName("chamar o agendador duas vezes no mesmo dia produz um artefato só")
    void agendadorDuasVezesProduzUm() {
        var repository = new InMemoryRepository();
        var service = service(repository, Set.of("reporting.dashboard.read"));
        var report = report(Schedule.DAILY);

        for (var at : List.of(NOW, NOW.plusSeconds(3600), NOW.plusSeconds(7200))) {
            service.execute(report, ReportExecutionService.scheduledKey(report, at), at);
        }

        assertThat(repository.runs).hasSize(1);
    }

    // --- cenário ---

    private static ReportExecutionService service(SavedReportRepository repository,
            Set<String> ownerPermissions) {
        DashboardQueries dashboard = (breweryId, from, to) ->
                new DashboardQueries.Dashboard(from, to, 0, List.of());
        BatchReportQueries batchReports = (breweryId, batchId) -> {
            throw new UnsupportedOperationException();
        };
        EffectivePermissionLookup permissions = (userId, breweryId) ->
                OWNER.equals(userId) ? ownerPermissions : Set.of();
        return new ReportExecutionService(repository, dashboard, batchReports, permissions,
                json());
    }

    /** O mesmo mapper que o Spring monta: sem os módulos de tempo, Instant não serializa. */
    private static ObjectMapper json() {
        return JsonMapper.builder().findAndAddModules().build();
    }

    private static SavedReport report(Schedule schedule) {
        return SavedReport.define(UUID.randomUUID(), "Painel", ReportKind.DASHBOARD, Map.of(),
                SAO_PAULO, ReportFormat.JSON, schedule, 30, OWNER, Set.of(UUID.randomUUID()), NOW,
                UUID.randomUUID());
    }

    /** Repositório de mentira, só com o que a execução usa. */
    private static final class InMemoryRepository implements SavedReportRepository {

        private final List<ReportRun> runs = new ArrayList<>();
        private final Map<String, ReportRun> byKey = new HashMap<>();

        @Override
        public ReportRun saveRun(ReportRun run) {
            runs.add(run);
            byKey.put(run.reportId() + "|" + run.idempotencyKey(), run);
            return run;
        }

        @Override
        public Optional<ReportRun> findRunByKey(UUID reportId, String idempotencyKey) {
            return Optional.ofNullable(byKey.get(reportId + "|" + idempotencyKey));
        }

        @Override public void save(SavedReport report) { }
        @Override public void update(SavedReport report) { }
        @Override public Optional<SavedReport> findById(UUID breweryId, UUID reportId) {
            return Optional.empty();
        }
        @Override public List<SavedReport> findAll(UUID breweryId) { return List.of(); }
        @Override public List<SavedReport> findScheduled() { return List.of(); }
        @Override public Optional<ReportRun> findRun(UUID breweryId, UUID runId) {
            return Optional.empty();
        }
        @Override public List<ReportRun> findRuns(UUID breweryId, UUID reportId) { return List.of(); }
        @Override public void updateDeliveries(ReportRun run) { }
        @Override public String issueToken(UUID breweryId, UUID runId, UUID userId, Instant expiresAt,
                Instant now) {
            return "token";
        }
        @Override public Optional<TokenGrant> findToken(String token) { return Optional.empty(); }
    }
}
