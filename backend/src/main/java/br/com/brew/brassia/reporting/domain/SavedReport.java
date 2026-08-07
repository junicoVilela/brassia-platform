package br.com.brew.brassia.reporting.domain;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Uma definição de relatório salva (RPT-003): o recorte, a periodicidade e para quem vai.
 *
 * <p><strong>É um acordo, não um cálculo.</strong> Alguém decidiu que este recorte, com esta
 * frequência, vai para estas pessoas — e por isso a definição se guarda, ao contrário do painel e
 * do dossiê, que se derivam. Acordo sem registro não existe.
 *
 * <p><strong>O proprietário técnico não é decoração.</strong> A execução programada roda sem
 * ninguém logado, e a alçada com que ela roda é a <em>de agora</em> do dono, resolvida na hora.
 * Congelar as permissões dele na definição criaria um privilégio que sobrevive à demissão; rodar
 * como sistema entregaria dado que ninguém autorizou.
 */
public final class SavedReport {

    private final UUID id;
    private final UUID breweryId;
    private final String name;
    private final ReportKind kind;
    private final int definitionVersion;
    private final Map<String, String> filters;
    private final ZoneId timezone;
    private final ReportFormat format;
    private final Schedule schedule;
    private final int retentionDays;
    private final UUID ownerUserId;
    private final Set<UUID> recipients;
    private final boolean active;
    private final Instant createdAt;
    private final UUID createdBy;
    private final long lockVersion;

    private SavedReport(UUID id, UUID breweryId, String name, ReportKind kind, int definitionVersion,
            Map<String, String> filters, ZoneId timezone, ReportFormat format, Schedule schedule,
            int retentionDays, UUID ownerUserId, Set<UUID> recipients, boolean active,
            Instant createdAt, UUID createdBy, long lockVersion) {
        this.id = Objects.requireNonNull(id);
        this.breweryId = Objects.requireNonNull(breweryId);
        this.name = requireName(name);
        this.kind = Objects.requireNonNull(kind, "tipo do relatório é obrigatório");
        this.definitionVersion = definitionVersion;
        this.filters = Map.copyOf(filters);
        this.timezone = Objects.requireNonNull(timezone, "fuso é obrigatório");
        this.format = Objects.requireNonNull(format, "formato é obrigatório");
        this.schedule = Objects.requireNonNull(schedule, "periodicidade é obrigatória");
        this.retentionDays = requireRetention(retentionDays);
        this.ownerUserId = Objects.requireNonNull(ownerUserId, "proprietário técnico é obrigatório");
        this.recipients = Set.copyOf(recipients);
        this.active = active;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.createdBy = Objects.requireNonNull(createdBy);
        this.lockVersion = lockVersion;
    }

    public static SavedReport define(UUID breweryId, String name, ReportKind kind,
            Map<String, String> filters, ZoneId timezone, ReportFormat format, Schedule schedule,
            int retentionDays, UUID ownerUserId, Set<UUID> recipients, Instant at, UUID actorId) {
        return new SavedReport(UUID.randomUUID(), breweryId, name, kind, 1, filters, timezone, format,
                schedule, retentionDays, ownerUserId, recipients, true, at, actorId, 0);
    }

    public static SavedReport reconstitute(UUID id, UUID breweryId, String name, ReportKind kind,
            int definitionVersion, Map<String, String> filters, ZoneId timezone, ReportFormat format,
            Schedule schedule, int retentionDays, UUID ownerUserId, Set<UUID> recipients,
            boolean active, Instant createdAt, UUID createdBy, long lockVersion) {
        return new SavedReport(id, breweryId, name, kind, definitionVersion, filters, timezone, format,
                schedule, retentionDays, ownerUserId, recipients, active, createdAt, createdBy,
                lockVersion);
    }

    /**
     * Reescreve o recorte e <strong>sobe a versão</strong>.
     *
     * <p>Sem isso, uma execução de março e uma de agosto diriam ter saído da mesma definição depois
     * de alguém ter trocado os filtros no meio — e o relatório antigo passaria a mentir sobre a
     * própria origem.
     */
    public SavedReport redefine(Map<String, String> filters, ZoneId timezone, Schedule schedule,
            int retentionDays, Set<UUID> recipients) {
        return new SavedReport(id, breweryId, name, kind, definitionVersion + 1, filters, timezone,
                format, schedule, retentionDays, ownerUserId, recipients, active, createdAt, createdBy,
                lockVersion);
    }

    public SavedReport activate(boolean value) {
        return new SavedReport(id, breweryId, name, kind, definitionVersion, filters, timezone, format,
                schedule, retentionDays, ownerUserId, recipients, value, createdAt, createdBy,
                lockVersion);
    }

    /** A permissão que o dono precisa ter <em>agora</em> para esta execução ser legítima. */
    public String requiredPermission() {
        return kind.requiredPermission();
    }

    public boolean scheduled() {
        return schedule != Schedule.MANUAL;
    }

    public Instant expiryFrom(Instant executedAt) {
        return executedAt.plus(java.time.Duration.ofDays(retentionDays));
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("nome do relatório é obrigatório");
        }
        if (name.length() > 120) {
            throw new IllegalArgumentException("nome do relatório é longo demais");
        }
        return name.strip();
    }

    private static int requireRetention(int days) {
        if (days < 1 || days > 3650) {
            throw new IllegalArgumentException("retenção deve ficar entre 1 e 3650 dias");
        }
        return days;
    }

    public UUID id() { return id; }
    public UUID breweryId() { return breweryId; }
    public String name() { return name; }
    public ReportKind kind() { return kind; }
    public int definitionVersion() { return definitionVersion; }
    public Map<String, String> filters() { return filters; }
    public ZoneId timezone() { return timezone; }
    public ReportFormat format() { return format; }
    public Schedule schedule() { return schedule; }
    public int retentionDays() { return retentionDays; }
    public UUID ownerUserId() { return ownerUserId; }
    public Set<UUID> recipients() { return recipients; }
    public boolean active() { return active; }
    public Instant createdAt() { return createdAt; }
    public UUID createdBy() { return createdBy; }
    public long lockVersion() { return lockVersion; }

    /** Os relatórios que sabem se produzir sozinhos. Ampliar esta lista amplia o que se programa. */
    public enum ReportKind {
        DASHBOARD("reporting.dashboard.read"),
        BATCH_REPORT("reporting.batch.read");

        private final String requiredPermission;

        ReportKind(String requiredPermission) {
            this.requiredPermission = requiredPermission;
        }

        public String requiredPermission() {
            return requiredPermission;
        }
    }

    /** Hoje só JSON, como a exportação manual (RPT-001-A). */
    public enum ReportFormat {
        JSON
    }

    public enum Schedule {
        MANUAL,
        DAILY,
        WEEKLY,
        MONTHLY
    }
}
