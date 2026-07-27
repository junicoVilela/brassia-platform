package br.com.brew.brassia.sanitation.adapter.outbound.persistence;

import br.com.brew.brassia.sanitation.application.port.outbound.ProcedureRepository;
import br.com.brew.brassia.sanitation.domain.CleaningProcedure;
import br.com.brew.brassia.sanitation.domain.ProcedureId;
import br.com.brew.brassia.sanitation.domain.ProcedureStatus;
import br.com.brew.brassia.sanitation.domain.ProcedureStep;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcProcedureRepository implements ProcedureRepository {

    private static final String COLUMNS = """
            SELECT id, brewery_id, code, name, version, status FROM sanitation_procedure
            """;

    private final JdbcClient jdbc;

    JdbcProcedureRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(CleaningProcedure p) {
        jdbc.sql("""
                INSERT INTO sanitation_procedure (id, brewery_id, code, name, version, status, created_at)
                VALUES (:id, :brewery, :code, :name, :version, :status, :at)
                """)
                .param("id", p.id().value())
                .param("brewery", p.breweryId())
                .param("code", p.code())
                .param("name", p.name())
                .param("version", p.version())
                .param("status", p.status().name())
                .param("at", Timestamp.from(Instant.now()))
                .update();
        insertSteps(p);
    }

    @Override
    public void update(CleaningProcedure p) {
        jdbc.sql("UPDATE sanitation_procedure SET name = :name WHERE id = :id AND brewery_id = :brewery")
                .param("name", p.name()).param("id", p.id().value()).param("brewery", p.breweryId())
                .update();
        jdbc.sql("DELETE FROM sanitation_procedure_step WHERE procedure_id = :id")
                .param("id", p.id().value()).update();
        insertSteps(p);
    }

    private void insertSteps(CleaningProcedure p) {
        for (var s : p.steps()) {
            jdbc.sql("""
                    INSERT INTO sanitation_procedure_step (
                        id, procedure_id, brewery_id, step_order, method, product, concentration_min_pct,
                        concentration_max_pct, temp_min_c, temp_max_c, time_minutes, flow, ppe, alternative,
                        prohibition, evidence_required)
                    VALUES (:id, :proc, :brewery, :seq, :method, :product, :cmin, :cmax, :tmin, :tmax, :time,
                            :flow, :ppe, :alt, :prohibition, :evidence)
                    """)
                    .param("id", s.id())
                    .param("proc", p.id().value())
                    .param("brewery", p.breweryId())
                    .param("seq", s.sequence())
                    .param("method", s.method())
                    .param("product", s.product())
                    .param("cmin", s.concentrationMinPct())
                    .param("cmax", s.concentrationMaxPct())
                    .param("tmin", s.tempMinC())
                    .param("tmax", s.tempMaxC())
                    .param("time", s.timeMinutes())
                    .param("flow", s.flow())
                    .param("ppe", s.ppe())
                    .param("alt", s.alternative())
                    .param("prohibition", s.prohibition())
                    .param("evidence", s.evidenceRequired())
                    .update();
        }
    }

    @Override
    public Optional<CleaningProcedure> findById(UUID breweryId, UUID procedureId) {
        return jdbc.sql(COLUMNS + " WHERE brewery_id = :brewery AND id = :id")
                .param("brewery", breweryId).param("id", procedureId)
                .query((rs, n) -> map(rs))
                .optional();
    }

    @Override
    public Optional<CleaningProcedure> findLatestByCode(UUID breweryId, String code) {
        return jdbc.sql(COLUMNS + " WHERE brewery_id = :brewery AND code = :code ORDER BY version DESC LIMIT 1")
                .param("brewery", breweryId).param("code", code)
                .query((rs, n) -> map(rs))
                .optional();
    }

    @Override
    public List<CleaningProcedure> findAll(UUID breweryId) {
        return jdbc.sql(COLUMNS + " WHERE brewery_id = :brewery ORDER BY code, version")
                .param("brewery", breweryId)
                .query((rs, n) -> map(rs))
                .list();
    }

    @Override
    public boolean markPublished(UUID breweryId, UUID procedureId) {
        int updated = jdbc.sql("""
                UPDATE sanitation_procedure SET status = 'PUBLISHED'
                WHERE brewery_id = :brewery AND id = :id AND status = 'DRAFT'
                """)
                .param("brewery", breweryId).param("id", procedureId)
                .update();
        return updated > 0;
    }

    private CleaningProcedure map(ResultSet rs) throws SQLException {
        var id = rs.getObject("id", UUID.class);
        var breweryId = rs.getObject("brewery_id", UUID.class);
        return CleaningProcedure.reconstitute(
                new ProcedureId(id),
                breweryId,
                rs.getString("code"),
                rs.getString("name"),
                rs.getInt("version"),
                ProcedureStatus.valueOf(rs.getString("status")),
                steps(breweryId, id));
    }

    private List<ProcedureStep> steps(UUID breweryId, UUID procedureId) {
        return jdbc.sql("""
                SELECT id, step_order, method, product, concentration_min_pct, concentration_max_pct, temp_min_c,
                       temp_max_c, time_minutes, flow, ppe, alternative, prohibition, evidence_required
                FROM sanitation_procedure_step
                WHERE brewery_id = :brewery AND procedure_id = :proc ORDER BY step_order
                """)
                .param("brewery", breweryId).param("proc", procedureId)
                .query((rs, n) -> new ProcedureStep(
                        rs.getObject("id", UUID.class),
                        rs.getInt("step_order"),
                        rs.getString("method"),
                        rs.getString("product"),
                        rs.getBigDecimal("concentration_min_pct"),
                        rs.getBigDecimal("concentration_max_pct"),
                        rs.getBigDecimal("temp_min_c"),
                        rs.getBigDecimal("temp_max_c"),
                        rs.getObject("time_minutes", Integer.class),
                        rs.getString("flow"),
                        rs.getString("ppe"),
                        rs.getString("alternative"),
                        rs.getString("prohibition"),
                        rs.getBoolean("evidence_required")))
                .list();
    }
}
