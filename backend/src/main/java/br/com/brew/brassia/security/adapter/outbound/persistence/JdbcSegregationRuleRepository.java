package br.com.brew.brassia.security.adapter.outbound.persistence;

import br.com.brew.brassia.security.application.port.outbound.SegregationRuleRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcSegregationRuleRepository implements SegregationRuleRepository {
    private final JdbcClient jdbc;

    JdbcSegregationRuleRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UUID create(UUID breweryId, String left, String right, String reason) {
        var id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO segregation_rule (id, brewery_id, left_permission_code, right_permission_code, reason)
                VALUES (:id, :breweryId, :left, :right, :reason)
                """)
                .param("id", id)
                .param("breweryId", breweryId)
                .param("left", left)
                .param("right", right)
                .param("reason", reason)
                .update();
        return id;
    }

    @Override
    public List<RuleView> listActive(UUID breweryId) {
        return jdbc.sql("""
                SELECT id, left_permission_code, right_permission_code, reason, active
                FROM segregation_rule WHERE brewery_id = :breweryId AND active
                """)
                .param("breweryId", breweryId)
                .query((rs, n) -> new RuleView(
                        rs.getObject("id", UUID.class),
                        rs.getString("left_permission_code"),
                        rs.getString("right_permission_code"),
                        rs.getString("reason"),
                        rs.getBoolean("active")))
                .list();
    }

    @Override
    public Optional<RuleView> findById(UUID id) {
        return jdbc.sql("""
                SELECT id, left_permission_code, right_permission_code, reason, active
                FROM segregation_rule WHERE id = :id
                """)
                .param("id", id)
                .query((rs, n) -> new RuleView(
                        rs.getObject("id", UUID.class),
                        rs.getString("left_permission_code"),
                        rs.getString("right_permission_code"),
                        rs.getString("reason"),
                        rs.getBoolean("active")))
                .optional();
    }
}
