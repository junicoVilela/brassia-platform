package br.com.brew.brassia.quality.adapter.outbound.persistence;

import br.com.brew.brassia.quality.application.port.outbound.NonConformityRepository;
import br.com.brew.brassia.quality.domain.CapaAction;
import br.com.brew.brassia.quality.domain.CapaActionKind;
import br.com.brew.brassia.quality.domain.Containment;
import br.com.brew.brassia.quality.domain.Investigation;
import br.com.brew.brassia.quality.domain.NonConformity;
import br.com.brew.brassia.quality.domain.NonConformitySource;
import br.com.brew.brassia.quality.domain.NonConformityStatus;
import br.com.brew.brassia.quality.domain.Severity;
import br.com.brew.brassia.quality.domain.Verification;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Cabeçalho, ações e verificações são lidos juntos: o tratamento só faz sentido inteiro, e decidir
 * sobre a próxima fase sem as ações abriria espaço para verificar eficácia de nada.
 *
 * <p>As ações são regravadas (apagar e reinserir) porque elas mudam — concluir muda a linha. As
 * <strong>verificações só entram</strong>: a negativa fica no histórico como evidência de que a
 * primeira tentativa não resolveu.
 */
@Repository
class JdbcNonConformityRepository implements NonConformityRepository {

    private static final String COLUMNS = """
            SELECT id, brewery_id, code, title, description, source, deviation_id, batch_id, severity, status,
                   containment_due_on, investigation_due_on, verification_due_on,
                   containment_description, containment_at, containment_by,
                   investigation_root_cause, investigation_method, investigation_at, investigation_by,
                   opened_at, opened_by, closed_at, closed_by, lock_version
            FROM quality_non_conformity
            """;

    private final JdbcClient jdbc;

    JdbcNonConformityRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(NonConformity nc) {
        jdbc.sql("""
                INSERT INTO quality_non_conformity (id, brewery_id, code, title, description, source,
                    deviation_id, batch_id, severity, status, containment_due_on, investigation_due_on,
                    verification_due_on, opened_at, opened_by, lock_version)
                VALUES (:id, :brewery, :code, :title, :description, :source, :deviation, :batch, :severity, :status,
                    :containmentDue, :investigationDue, :verificationDue, :openedAt, :openedBy, 0)
                """)
                .param("id", nc.id()).param("brewery", nc.breweryId()).param("code", nc.code())
                .param("title", nc.title()).param("description", nc.description())
                .param("source", nc.source().name()).param("deviation", nc.deviationId().orElse(null))
                .param("batch", nc.batchId().orElse(null))
                .param("severity", nc.severity().name()).param("status", nc.status().name())
                .param("containmentDue", nc.containmentDueOn())
                .param("investigationDue", nc.investigationDueOn())
                .param("verificationDue", nc.verificationDueOn())
                .param("openedAt", Timestamp.from(nc.openedAt())).param("openedBy", nc.openedBy())
                .update();
    }

    @Override
    public long nextSequence(UUID breweryId, int year) {
        return jdbc.sql("""
                INSERT INTO quality_nc_sequence (brewery_id, year, next_val) VALUES (:brewery, :year, 1)
                ON CONFLICT (brewery_id, year) DO UPDATE SET next_val = quality_nc_sequence.next_val + 1
                RETURNING next_val
                """)
                .param("brewery", breweryId).param("year", year)
                .query(Long.class).single();
    }

    @Override
    public void update(NonConformity nc) {
        jdbc.sql("""
                UPDATE quality_non_conformity
                SET status = :status,
                    containment_description = :containmentDescription, containment_at = :containmentAt,
                    containment_by = :containmentBy,
                    investigation_root_cause = :rootCause, investigation_method = :method,
                    investigation_at = :investigationAt, investigation_by = :investigationBy,
                    closed_at = :closedAt, closed_by = :closedBy, lock_version = lock_version + 1
                WHERE id = :id AND brewery_id = :brewery
                """)
                .param("status", nc.status().name())
                .param("containmentDescription", nc.containment().map(Containment::description).orElse(null))
                .param("containmentAt", nc.containment().map(c -> Timestamp.from(c.takenAt())).orElse(null))
                .param("containmentBy", nc.containment().map(Containment::takenBy).orElse(null))
                .param("rootCause", nc.investigation().map(Investigation::rootCause).orElse(null))
                .param("method", nc.investigation().map(Investigation::method).orElse(null))
                .param("investigationAt",
                        nc.investigation().map(i -> Timestamp.from(i.investigatedAt())).orElse(null))
                .param("investigationBy", nc.investigation().map(Investigation::investigatedBy).orElse(null))
                .param("closedAt", nc.closedAt() == null ? null : Timestamp.from(nc.closedAt()))
                .param("closedBy", nc.closedBy())
                .param("id", nc.id()).param("brewery", nc.breweryId())
                .update();

        jdbc.sql("DELETE FROM quality_capa_action WHERE non_conformity_id = :nc")
                .param("nc", nc.id()).update();
        for (var action : nc.actions()) {
            jdbc.sql("""
                    INSERT INTO quality_capa_action (id, brewery_id, non_conformity_id, kind, description,
                        owner, due_on, completed_at)
                    VALUES (:id, :brewery, :nc, :kind, :description, :owner, :dueOn, :completedAt)
                    """)
                    .param("id", action.id()).param("brewery", nc.breweryId()).param("nc", nc.id())
                    .param("kind", action.kind().name()).param("description", action.description())
                    .param("owner", action.owner()).param("dueOn", action.dueOn())
                    .param("completedAt",
                            action.completedAt() == null ? null : Timestamp.from(action.completedAt()))
                    .update();
        }

        // Verificações só entram: a negativa é evidência e não se apaga.
        var gravadas = jdbc.sql("SELECT count(*) FROM quality_verification WHERE non_conformity_id = :nc")
                .param("nc", nc.id()).query(Integer.class).single();
        var todas = nc.verifications();
        for (int i = gravadas; i < todas.size(); i++) {
            var v = todas.get(i);
            jdbc.sql("""
                    INSERT INTO quality_verification (id, brewery_id, non_conformity_id, effective, evidence,
                        verified_at, verified_by)
                    VALUES (:id, :brewery, :nc, :effective, :evidence, :at, :by)
                    """)
                    .param("id", UUID.randomUUID()).param("brewery", nc.breweryId()).param("nc", nc.id())
                    .param("effective", v.effective()).param("evidence", v.evidence())
                    .param("at", Timestamp.from(v.verifiedAt())).param("by", v.verifiedBy())
                    .update();
        }
    }

    @Override
    public Optional<NonConformity> findById(UUID breweryId, UUID id) {
        return load(breweryId, id, "");
    }

    @Override
    public Optional<NonConformity> lockById(UUID breweryId, UUID id) {
        return load(breweryId, id, " FOR UPDATE");
    }

    private Optional<NonConformity> load(UUID breweryId, UUID id, String lock) {
        return jdbc.sql(COLUMNS + " WHERE brewery_id = :brewery AND id = :id" + lock)
                .param("brewery", breweryId).param("id", id)
                .query((rs, n) -> map(rs))
                .optional();
    }

    @Override
    public List<NonConformity> findAll(UUID breweryId) {
        return jdbc.sql(COLUMNS + " WHERE brewery_id = :brewery ORDER BY opened_at DESC, code")
                .param("brewery", breweryId)
                .query((rs, n) -> map(rs))
                .list();
    }

    @Override
    public boolean existsByCode(UUID breweryId, String code) {
        return jdbc.sql("SELECT 1 FROM quality_non_conformity WHERE brewery_id = :brewery AND code = :code")
                .param("brewery", breweryId).param("code", code)
                .query(Integer.class).optional().isPresent();
    }

    private NonConformity map(ResultSet rs) throws SQLException {
        var id = rs.getObject("id", UUID.class);
        var containmentAt = rs.getTimestamp("containment_at");
        var investigationAt = rs.getTimestamp("investigation_at");
        var closedAt = rs.getTimestamp("closed_at");
        return NonConformity.reconstitute(id,
                rs.getObject("brewery_id", UUID.class),
                rs.getString("code"),
                rs.getString("title"),
                rs.getString("description"),
                NonConformitySource.valueOf(rs.getString("source")),
                rs.getObject("deviation_id", UUID.class),
                rs.getObject("batch_id", UUID.class),
                Severity.valueOf(rs.getString("severity")),
                NonConformityStatus.valueOf(rs.getString("status")),
                rs.getObject("containment_due_on", LocalDate.class),
                rs.getObject("investigation_due_on", LocalDate.class),
                rs.getObject("verification_due_on", LocalDate.class),
                containmentAt == null ? null : new Containment(rs.getString("containment_description"),
                        containmentAt.toInstant(), rs.getObject("containment_by", UUID.class)),
                investigationAt == null ? null : new Investigation(rs.getString("investigation_root_cause"),
                        rs.getString("investigation_method"), investigationAt.toInstant(),
                        rs.getObject("investigation_by", UUID.class)),
                actions(id),
                verifications(id),
                rs.getTimestamp("opened_at").toInstant(),
                rs.getObject("opened_by", UUID.class),
                closedAt == null ? null : closedAt.toInstant(),
                rs.getObject("closed_by", UUID.class),
                rs.getLong("lock_version"));
    }

    private List<CapaAction> actions(UUID nonConformityId) {
        return jdbc.sql("""
                SELECT id, kind, description, owner, due_on, completed_at
                FROM quality_capa_action WHERE non_conformity_id = :nc ORDER BY due_on, id
                """)
                .param("nc", nonConformityId)
                .query((rs, n) -> {
                    var completed = rs.getTimestamp("completed_at");
                    return CapaAction.reconstitute(rs.getObject("id", UUID.class),
                            CapaActionKind.valueOf(rs.getString("kind")),
                            rs.getString("description"), rs.getString("owner"),
                            rs.getObject("due_on", LocalDate.class),
                            completed == null ? null : completed.toInstant());
                })
                .list();
    }

    private List<Verification> verifications(UUID nonConformityId) {
        return jdbc.sql("""
                SELECT effective, evidence, verified_at, verified_by
                FROM quality_verification WHERE non_conformity_id = :nc ORDER BY verified_at, id
                """)
                .param("nc", nonConformityId)
                .query((rs, n) -> new Verification(rs.getBoolean("effective"), rs.getString("evidence"),
                        rs.getTimestamp("verified_at").toInstant(),
                        rs.getObject("verified_by", UUID.class)))
                .list();
    }

    @SuppressWarnings("unused")
    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
