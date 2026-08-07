package br.com.brew.brassia.reporting.adapter.outbound.persistence;

import br.com.brew.brassia.reporting.application.port.outbound.SavedReportRepository;
import br.com.brew.brassia.reporting.domain.ReportRun;
import br.com.brew.brassia.reporting.domain.SavedReport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Persistência dos relatórios salvos, execuções, entregas e links (RPT-003). */
@Repository
class JdbcSavedReportRepository implements SavedReportRepository {

    /** 32 bytes de aleatoriedade: token de download é credencial, não identificador legível. */
    private static final int TOKEN_BYTES = 32;

    private static final String REPORT_COLUMNS = """
            id, brewery_id, name, kind, definition_version, filters, timezone, format, schedule,
            retention_days, owner_user_id, active, created_at, created_by, lock_version
            """;

    /** Mapper próprio, como nos demais adaptadores: aqui só se serializa um mapa de texto. */
    private static final ObjectMapper JSON = new ObjectMapper();

    private final JdbcClient jdbc;
    private final SecureRandom random = new SecureRandom();

    JdbcSavedReportRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public void save(SavedReport report) {
        jdbc.sql("""
                INSERT INTO reporting_saved_report (id, brewery_id, name, kind, definition_version,
                    filters, timezone, format, schedule, retention_days, owner_user_id, active,
                    created_at, created_by, lock_version)
                VALUES (:id, :brewery, :name, :kind, :version, :filters::jsonb, :timezone, :format,
                    :schedule, :retention, :owner, :active, :createdAt, :createdBy, :lock)
                """)
                .param("id", report.id()).param("brewery", report.breweryId())
                .param("name", report.name()).param("kind", report.kind().name())
                .param("version", report.definitionVersion())
                .param("filters", write(report.filters()))
                .param("timezone", report.timezone().getId())
                .param("format", report.format().name())
                .param("schedule", report.schedule().name())
                .param("retention", report.retentionDays())
                .param("owner", report.ownerUserId()).param("active", report.active())
                .param("createdAt", Timestamp.from(report.createdAt()))
                .param("createdBy", report.createdBy()).param("lock", report.lockVersion())
                .update();
        replaceRecipients(report);
    }

    @Override
    public void update(SavedReport report) {
        var updated = jdbc.sql("""
                UPDATE reporting_saved_report
                SET definition_version = :version, filters = :filters::jsonb, timezone = :timezone,
                    schedule = :schedule, retention_days = :retention, active = :active,
                    lock_version = lock_version + 1
                WHERE id = :id AND brewery_id = :brewery AND lock_version = :lock
                """)
                .param("id", report.id()).param("brewery", report.breweryId())
                .param("version", report.definitionVersion())
                .param("filters", write(report.filters()))
                .param("timezone", report.timezone().getId())
                .param("schedule", report.schedule().name())
                .param("retention", report.retentionDays()).param("active", report.active())
                .param("lock", report.lockVersion())
                .update();
        if (updated == 0) {
            // Alguém salvou por cima entre a leitura e a escrita: recusar é melhor do que sobrescrever
            // uma definição que outro operador acabou de mudar.
            throw new org.springframework.dao.OptimisticLockingFailureException(
                    "a definição foi alterada por outra pessoa");
        }
        replaceRecipients(report);
    }

    @Override
    public Optional<SavedReport> findById(UUID breweryId, UUID reportId) {
        return jdbc.sql("SELECT " + REPORT_COLUMNS
                        + " FROM reporting_saved_report WHERE brewery_id = :brewery AND id = :id")
                .param("brewery", breweryId).param("id", reportId)
                .query(this::mapReport).optional();
    }

    @Override
    public List<SavedReport> findAll(UUID breweryId) {
        return jdbc.sql("SELECT " + REPORT_COLUMNS
                        + " FROM reporting_saved_report WHERE brewery_id = :brewery ORDER BY name")
                .param("brewery", breweryId).query(this::mapReport).list();
    }

    @Override
    public List<SavedReport> findScheduled() {
        return jdbc.sql("SELECT " + REPORT_COLUMNS + " FROM reporting_saved_report "
                        + "WHERE active AND schedule <> 'MANUAL' ORDER BY brewery_id, name")
                .query(this::mapReport).list();
    }

    @Override
    public ReportRun saveRun(ReportRun run) {
        try {
            jdbc.sql("""
                    INSERT INTO reporting_report_run (id, report_id, brewery_id, definition_version,
                        idempotency_key, status, refusal_reason, content, period_from, period_to,
                        expires_at, executed_at)
                    VALUES (:id, :report, :brewery, :version, :key, :status, :reason, :content::jsonb,
                        :from, :to, :expires, :executedAt)
                    """)
                    .param("id", run.id()).param("report", run.reportId())
                    .param("brewery", run.breweryId()).param("version", run.definitionVersion())
                    .param("key", run.idempotencyKey()).param("status", run.status().name())
                    .param("reason", run.refusalReason()).param("content", run.content())
                    .param("from", timestamp(run.periodFrom())).param("to", timestamp(run.periodTo()))
                    .param("expires", timestamp(run.expiresAt()))
                    .param("executedAt", Timestamp.from(run.executedAt()))
                    .update();
        } catch (DuplicateKeyException ex) {
            // Dois agendadores correndo pela mesma chave: o índice único decide, e o perdedor lê a
            // execução do vencedor em vez de criar a segunda.
            return findRunByKey(run.reportId(), run.idempotencyKey()).orElseThrow(() -> ex);
        }
        insertDeliveries(run);
        return run;
    }

    @Override
    public Optional<ReportRun> findRun(UUID breweryId, UUID runId) {
        return jdbc.sql("SELECT * FROM reporting_report_run WHERE brewery_id = :brewery AND id = :id")
                .param("brewery", breweryId).param("id", runId)
                .query(this::mapRun).optional()
                .map(this::withDeliveries);
    }

    @Override
    public Optional<ReportRun> findRunByKey(UUID reportId, String idempotencyKey) {
        return jdbc.sql("SELECT * FROM reporting_report_run "
                        + "WHERE report_id = :report AND idempotency_key = :key")
                .param("report", reportId).param("key", idempotencyKey)
                .query(this::mapRun).optional()
                .map(this::withDeliveries);
    }

    @Override
    public List<ReportRun> findRuns(UUID breweryId, UUID reportId) {
        return jdbc.sql("SELECT * FROM reporting_report_run "
                        + "WHERE brewery_id = :brewery AND report_id = :report "
                        + "ORDER BY executed_at DESC")
                .param("brewery", breweryId).param("report", reportId)
                .query(this::mapRun).list().stream()
                .map(this::withDeliveries)
                .toList();
    }

    @Override
    public void updateDeliveries(ReportRun run) {
        for (var delivery : run.deliveryList()) {
            jdbc.sql("""
                    UPDATE reporting_report_delivery
                    SET status = :status, detail = :detail, attempts = :attempts,
                        last_attempt_at = :at
                    WHERE run_id = :run AND user_id = :user
                    """)
                    .param("run", run.id()).param("user", delivery.userId())
                    .param("status", delivery.status().name()).param("detail", delivery.detail())
                    .param("attempts", delivery.attempts())
                    .param("at", timestamp(delivery.lastAttemptAt()))
                    .update();
        }
    }

    @Override
    public String issueToken(UUID breweryId, UUID runId, UUID userId, Instant expiresAt, Instant now) {
        var bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        var token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.sql("""
                INSERT INTO reporting_download_token (token, run_id, brewery_id, user_id, expires_at,
                    created_at)
                VALUES (:token, :run, :brewery, :user, :expires, :now)
                """)
                .param("token", token).param("run", runId).param("brewery", breweryId)
                .param("user", userId).param("expires", Timestamp.from(expiresAt))
                .param("now", Timestamp.from(now))
                .update();
        return token;
    }

    @Override
    public Optional<TokenGrant> findToken(String token) {
        return jdbc.sql("SELECT * FROM reporting_download_token WHERE token = :token")
                .param("token", token)
                .query((rs, rowNum) -> new TokenGrant(rs.getString("token"),
                        rs.getObject("run_id", UUID.class), rs.getObject("brewery_id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getTimestamp("expires_at").toInstant()))
                .optional();
    }

    // --- mapeamento ---

    private void replaceRecipients(SavedReport report) {
        jdbc.sql("DELETE FROM reporting_saved_report_recipient WHERE report_id = :report")
                .param("report", report.id()).update();
        for (UUID recipient : report.recipients()) {
            jdbc.sql("""
                    INSERT INTO reporting_saved_report_recipient (report_id, brewery_id, user_id)
                    VALUES (:report, :brewery, :user)
                    """)
                    .param("report", report.id()).param("brewery", report.breweryId())
                    .param("user", recipient)
                    .update();
        }
    }

    private void insertDeliveries(ReportRun run) {
        for (var delivery : run.deliveryList()) {
            jdbc.sql("""
                    INSERT INTO reporting_report_delivery (run_id, brewery_id, user_id, status,
                        attempts)
                    VALUES (:run, :brewery, :user, :status, :attempts)
                    ON CONFLICT (run_id, user_id) DO NOTHING
                    """)
                    .param("run", run.id()).param("brewery", run.breweryId())
                    .param("user", delivery.userId()).param("status", delivery.status().name())
                    .param("attempts", delivery.attempts())
                    .update();
        }
    }

    private SavedReport mapReport(ResultSet rs, int rowNum) throws SQLException {
        var id = rs.getObject("id", UUID.class);
        return SavedReport.reconstitute(id, rs.getObject("brewery_id", UUID.class),
                rs.getString("name"), SavedReport.ReportKind.valueOf(rs.getString("kind")),
                rs.getInt("definition_version"), read(rs.getString("filters")),
                ZoneId.of(rs.getString("timezone")),
                SavedReport.ReportFormat.valueOf(rs.getString("format")),
                SavedReport.Schedule.valueOf(rs.getString("schedule")), rs.getInt("retention_days"),
                rs.getObject("owner_user_id", UUID.class), recipientsOf(id), rs.getBoolean("active"),
                rs.getTimestamp("created_at").toInstant(), rs.getObject("created_by", UUID.class),
                rs.getLong("lock_version"));
    }

    private java.util.Set<UUID> recipientsOf(UUID reportId) {
        return new LinkedHashSet<>(jdbc
                .sql("SELECT user_id FROM reporting_saved_report_recipient WHERE report_id = :report "
                        + "ORDER BY user_id")
                .param("report", reportId).query(UUID.class).list());
    }

    private ReportRun mapRun(ResultSet rs, int rowNum) throws SQLException {
        return ReportRun.reconstitute(rs.getObject("id", UUID.class),
                rs.getObject("report_id", UUID.class), rs.getObject("brewery_id", UUID.class),
                rs.getInt("definition_version"), rs.getString("idempotency_key"),
                ReportRun.Status.valueOf(rs.getString("status")), rs.getString("refusal_reason"),
                rs.getString("content"), instant(rs.getTimestamp("period_from")),
                instant(rs.getTimestamp("period_to")), instant(rs.getTimestamp("expires_at")),
                rs.getTimestamp("executed_at").toInstant(), Map.of());
    }

    private ReportRun withDeliveries(ReportRun run) {
        var deliveries = new LinkedHashMap<UUID, ReportRun.Delivery>();
        jdbc.sql("SELECT * FROM reporting_report_delivery WHERE run_id = :run ORDER BY user_id")
                .param("run", run.id())
                .query((rs, rowNum) -> new ReportRun.Delivery(rs.getObject("user_id", UUID.class),
                        ReportRun.Delivery.Status.valueOf(rs.getString("status")),
                        rs.getString("detail"), rs.getInt("attempts"),
                        instant(rs.getTimestamp("last_attempt_at"))))
                .list()
                .forEach(delivery -> deliveries.put(delivery.userId(), delivery));
        return ReportRun.reconstitute(run.id(), run.reportId(), run.breweryId(),
                run.definitionVersion(), run.idempotencyKey(), run.status(), run.refusalReason(),
                run.content(), run.periodFrom(), run.periodTo(), run.expiresAt(), run.executedAt(),
                deliveries);
    }

    private String write(Map<String, String> filters) {
        try {
            return JSON.writeValueAsString(filters);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("filtros inválidos", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> read(String filters) {
        try {
            return filters == null ? Map.of() : JSON.readValue(filters, Map.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("filtros gravados são ilegíveis", ex);
        }
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
