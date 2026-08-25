package br.com.brew.brassia.security.adapter.outbound.persistence;

import br.com.brew.brassia.security.application.port.outbound.SecurityAlertRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcSecurityAlertRepository implements SecurityAlertRepository {
    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    JdbcSecurityAlertRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UUID create(UUID breweryId, UUID userId, String alertType, String severity, Map<String, Object> evidence) {
        var id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO security_alert (id, brewery_id, user_id, alert_type, severity, status, evidence)
                VALUES (:id, :breweryId, :userId, :type, :severity, 'OPEN', :evidence::jsonb)
                """)
                .param("id", id)
                .param("breweryId", breweryId)
                .param("userId", userId)
                .param("type", alertType)
                .param("severity", severity)
                .param("evidence", toJson(evidence))
                .update();
        return id;
    }

    /**
     * Alertas da cervejaria, opcionalmente filtrados por status.
     *
     * <p><strong>O {@code CAST(:status AS VARCHAR)} é obrigatório, e não estilo.</strong> Sem ele o
     * PostgreSQL não consegue inferir o tipo do parâmetro em {@code :status IS NULL} e recusa a instrução
     * com "could not determine data type of parameter" — ou seja, listar alertas <em>sem filtro</em>
     * respondia 500, que é justamente como a tela abre. Só a listagem já filtrada funcionava.
     */
    @Override
    public List<AlertView> listByBrewery(UUID breweryId, String status, int limit) {
        return jdbc.sql("""
                SELECT id, brewery_id, user_id, alert_type, severity, status, evidence, created_at
                FROM security_alert
                WHERE brewery_id = :breweryId
                  AND (CAST(:status AS VARCHAR) IS NULL OR status = :status)
                ORDER BY created_at DESC LIMIT :limit
                """)
                .param("breweryId", breweryId)
                .param("status", status)
                .param("limit", limit)
                .query((rs, n) -> new AlertView(
                        rs.getObject("id", UUID.class),
                        rs.getObject("brewery_id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getString("alert_type"),
                        rs.getString("severity"),
                        rs.getString("status"),
                        parseEvidence(rs.getString("evidence")),
                        rs.getTimestamp("created_at").toInstant()))
                .list();
    }

    @Override
    public Optional<AlertView> findById(UUID breweryId, UUID id) {
        return jdbc.sql("""
                SELECT id, brewery_id, user_id, alert_type, severity, status, evidence, created_at
                FROM security_alert WHERE id = :id AND brewery_id = :brewery
                """)
                .param("id", id)
                .param("brewery", breweryId)
                .query((rs, n) -> new AlertView(
                        rs.getObject("id", UUID.class),
                        rs.getObject("brewery_id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getString("alert_type"),
                        rs.getString("severity"),
                        rs.getString("status"),
                        parseEvidence(rs.getString("evidence")),
                        rs.getTimestamp("created_at").toInstant()))
                .optional();
    }

    @Override
    public void updateStatus(UUID breweryId, UUID id, String status, UUID resolvedBy) {
        jdbc.sql("""
                UPDATE security_alert SET status = :status, resolved_at = now(), resolved_by = :resolvedBy
                WHERE id = :id AND brewery_id = :brewery
                """)
                .param("status", status)
                .param("resolvedBy", resolvedBy)
                .param("brewery", breweryId)
                .param("id", id)
                .update();
    }

    private String toJson(Map<String, Object> evidence) {
        try {
            return objectMapper.writeValueAsString(evidence);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, Object> parseEvidence(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }
}
