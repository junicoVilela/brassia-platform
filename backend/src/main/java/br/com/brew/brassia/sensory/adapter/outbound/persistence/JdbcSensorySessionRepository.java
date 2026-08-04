package br.com.brew.brassia.sensory.adapter.outbound.persistence;

import br.com.brew.brassia.sensory.application.port.outbound.SensorySessionRepository;
import br.com.brew.brassia.sensory.domain.BlindCode;
import br.com.brew.brassia.sensory.domain.SensoryAttribute;
import br.com.brew.brassia.sensory.domain.SensoryEvaluation;
import br.com.brew.brassia.sensory.domain.SensorySample;
import br.com.brew.brassia.sensory.domain.SensorySession;
import br.com.brew.brassia.sensory.domain.SessionStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * A sessão é lida com as suas amostras: julgar a cegueira sem saber quais amostras existem seria
 * decidir sobre metade do estado.
 *
 * <p>As <strong>fichas só entram</strong> — não há update em lugar nenhum deste repositório, o que
 * torna a imutabilidade da avaliação uma propriedade da persistência, não só do domínio.
 */
@Repository
class JdbcSensorySessionRepository implements SensorySessionRepository {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<String>> DESCRIPTORS = new TypeReference<>() {};

    private static final String COLUMNS = """
            SELECT id, brewery_id, code, purpose, scheduled_for, status, opened_at, closed_at, lock_version
            FROM sensory_session
            """;

    private final JdbcClient jdbc;

    JdbcSensorySessionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(SensorySession s) {
        jdbc.sql("""
                INSERT INTO sensory_session (id, brewery_id, code, purpose, scheduled_for, status,
                    opened_at, closed_at, lock_version)
                VALUES (:id, :brewery, :code, :purpose, :scheduledFor, :status, NULL, NULL, 0)
                """)
                .param("id", s.id()).param("brewery", s.breweryId()).param("code", s.code())
                .param("purpose", s.purpose()).param("scheduledFor", s.scheduledFor())
                .param("status", s.status().name())
                .update();
        insertSamples(s);
    }

    @Override
    public void update(SensorySession s) {
        jdbc.sql("""
                UPDATE sensory_session
                SET purpose = :purpose, scheduled_for = :scheduledFor, status = :status,
                    opened_at = :openedAt, closed_at = :closedAt, lock_version = lock_version + 1
                WHERE id = :id AND brewery_id = :brewery
                """)
                .param("purpose", s.purpose()).param("scheduledFor", s.scheduledFor())
                .param("status", s.status().name())
                .param("openedAt", s.openedAt() == null ? null : Timestamp.from(s.openedAt()))
                .param("closedAt", s.closedAt() == null ? null : Timestamp.from(s.closedAt()))
                .param("id", s.id()).param("brewery", s.breweryId())
                .update();

        // Amostras só mudam com a sessão em rascunho, e aí nenhuma ficha aponta para elas ainda.
        if (s.status() == SessionStatus.DRAFT) {
            jdbc.sql("DELETE FROM sensory_sample WHERE session_id = :session")
                    .param("session", s.id()).update();
            insertSamples(s);
        }
    }

    private void insertSamples(SensorySession s) {
        for (var sample : s.samples()) {
            jdbc.sql("""
                    INSERT INTO sensory_sample (id, brewery_id, session_id, blind_code, batch_id, note)
                    VALUES (:id, :brewery, :session, :blindCode, :batch, :note)
                    """)
                    .param("id", sample.id()).param("brewery", s.breweryId()).param("session", s.id())
                    .param("blindCode", sample.blindCode().value()).param("batch", sample.batchId())
                    .param("note", sample.note())
                    .update();
        }
    }

    @Override
    public Optional<SensorySession> findById(UUID breweryId, UUID sessionId) {
        return load(breweryId, sessionId, "");
    }

    @Override
    public Optional<SensorySession> lockById(UUID breweryId, UUID sessionId) {
        return load(breweryId, sessionId, " FOR UPDATE");
    }

    private Optional<SensorySession> load(UUID breweryId, UUID sessionId, String lock) {
        return jdbc.sql(COLUMNS + " WHERE brewery_id = :brewery AND id = :id" + lock)
                .param("brewery", breweryId).param("id", sessionId)
                .query((rs, n) -> map(rs))
                .optional();
    }

    @Override
    public List<SensorySession> findAll(UUID breweryId) {
        return jdbc.sql(COLUMNS + " WHERE brewery_id = :brewery ORDER BY scheduled_for DESC, code")
                .param("brewery", breweryId)
                .query((rs, n) -> map(rs))
                .list();
    }

    @Override
    public boolean existsByCode(UUID breweryId, String code) {
        return jdbc.sql("SELECT 1 FROM sensory_session WHERE brewery_id = :brewery AND code = :code")
                .param("brewery", breweryId).param("code", code)
                .query(Integer.class).optional().isPresent();
    }

    @Override
    public void insertEvaluation(SensoryEvaluation e) {
        jdbc.sql("""
                INSERT INTO sensory_evaluation (id, brewery_id, session_id, sample_id, taster_id,
                    appearance, aroma, flavor, body, overall, descriptors, note, submitted_at)
                VALUES (:id, :brewery, :session, :sample, :taster, :appearance, :aroma, :flavor, :body,
                    :overall, CAST(:descriptors AS jsonb), :note, :at)
                """)
                .param("id", e.id()).param("brewery", e.breweryId()).param("session", e.sessionId())
                .param("sample", e.sampleId()).param("taster", e.tasterId())
                .param("appearance", e.score(SensoryAttribute.APPEARANCE))
                .param("aroma", e.score(SensoryAttribute.AROMA))
                .param("flavor", e.score(SensoryAttribute.FLAVOR))
                .param("body", e.score(SensoryAttribute.BODY))
                .param("overall", e.score(SensoryAttribute.OVERALL))
                .param("descriptors", toJson(e.descriptors()))
                .param("note", e.note()).param("at", Timestamp.from(e.submittedAt()))
                .update();
    }

    @Override
    public List<SensoryEvaluation> findEvaluations(UUID breweryId, UUID sessionId) {
        return jdbc.sql("""
                SELECT id, brewery_id, session_id, sample_id, taster_id, appearance, aroma, flavor, body,
                       overall, descriptors, note, submitted_at
                FROM sensory_evaluation
                WHERE brewery_id = :brewery AND session_id = :session
                ORDER BY submitted_at, id
                """)
                .param("brewery", breweryId).param("session", sessionId)
                .query((rs, n) -> mapEvaluation(rs))
                .list();
    }

    @Override
    public boolean hasEvaluated(UUID breweryId, UUID sampleId, UUID tasterId) {
        return jdbc.sql("""
                SELECT 1 FROM sensory_evaluation
                WHERE brewery_id = :brewery AND sample_id = :sample AND taster_id = :taster
                """)
                .param("brewery", breweryId).param("sample", sampleId).param("taster", tasterId)
                .query(Integer.class).optional().isPresent();
    }

    @Override
    public int countEvaluations(UUID breweryId, UUID sessionId) {
        return jdbc.sql("""
                SELECT count(*) FROM sensory_evaluation
                WHERE brewery_id = :brewery AND session_id = :session
                """)
                .param("brewery", breweryId).param("session", sessionId)
                .query(Integer.class).single();
    }

    private SensorySession map(ResultSet rs) throws SQLException {
        var id = rs.getObject("id", UUID.class);
        var openedAt = rs.getTimestamp("opened_at");
        var closedAt = rs.getTimestamp("closed_at");
        return SensorySession.reconstitute(id,
                rs.getObject("brewery_id", UUID.class),
                rs.getString("code"),
                rs.getString("purpose"),
                rs.getObject("scheduled_for", LocalDate.class),
                SessionStatus.valueOf(rs.getString("status")),
                samples(id),
                openedAt == null ? null : openedAt.toInstant(),
                closedAt == null ? null : closedAt.toInstant(),
                rs.getLong("lock_version"));
    }

    private List<SensorySample> samples(UUID sessionId) {
        return jdbc.sql("""
                SELECT id, blind_code, batch_id, note FROM sensory_sample
                WHERE session_id = :session ORDER BY blind_code
                """)
                .param("session", sessionId)
                .query((rs, n) -> SensorySample.reconstitute(rs.getObject("id", UUID.class),
                        new BlindCode(rs.getString("blind_code")),
                        rs.getObject("batch_id", UUID.class),
                        rs.getString("note")))
                .list();
    }

    private SensoryEvaluation mapEvaluation(ResultSet rs) throws SQLException {
        var scores = new EnumMap<SensoryAttribute, Integer>(SensoryAttribute.class);
        scores.put(SensoryAttribute.APPEARANCE, rs.getInt("appearance"));
        scores.put(SensoryAttribute.AROMA, rs.getInt("aroma"));
        scores.put(SensoryAttribute.FLAVOR, rs.getInt("flavor"));
        scores.put(SensoryAttribute.BODY, rs.getInt("body"));
        scores.put(SensoryAttribute.OVERALL, rs.getInt("overall"));
        return SensoryEvaluation.reconstitute(
                rs.getObject("id", UUID.class),
                rs.getObject("brewery_id", UUID.class),
                rs.getObject("session_id", UUID.class),
                rs.getObject("sample_id", UUID.class),
                rs.getObject("taster_id", UUID.class),
                scores,
                fromJson(rs.getString("descriptors")),
                rs.getString("note"),
                rs.getTimestamp("submitted_at").toInstant());
    }

    private static String toJson(List<String> descriptors) {
        try {
            return JSON.writeValueAsString(descriptors);
        } catch (Exception e) {
            throw new IllegalStateException("falha ao serializar descritores", e);
        }
    }

    private static List<String> fromJson(String json) {
        try {
            return JSON.readValue(json, DESCRIPTORS);
        } catch (Exception e) {
            throw new IllegalStateException("falha ao ler descritores", e);
        }
    }
}
