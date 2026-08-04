package br.com.brew.brassia.sensory.adapter.outbound.persistence;

import br.com.brew.brassia.sensory.application.port.outbound.SensoryPolicyRepository;
import br.com.brew.brassia.sensory.domain.SensoryPolicy;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Sem linha, a escala é a padrão de 10 — a mesma das sessões criadas antes da PRM-001. */
@Repository
class JdbcSensoryPolicyRepository implements SensoryPolicyRepository {

    private final JdbcClient jdbc;

    JdbcSensoryPolicyRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public SensoryPolicy find(UUID breweryId) {
        return jdbc.sql("SELECT brewery_id, max_score, version FROM sensory_policy "
                        + "WHERE brewery_id = :brewery")
                .param("brewery", breweryId)
                .query((rs, n) -> SensoryPolicy.reconstitute(rs.getObject("brewery_id", UUID.class),
                        rs.getInt("max_score"), rs.getLong("version")))
                .optional()
                .orElseGet(() -> SensoryPolicy.defaults(breweryId));
    }

    @Override
    public void save(SensoryPolicy policy) {
        jdbc.sql("""
                INSERT INTO sensory_policy (brewery_id, max_score, version)
                VALUES (:brewery, :maxScore, 0)
                ON CONFLICT (brewery_id) DO UPDATE
                SET max_score = :maxScore, version = sensory_policy.version + 1
                """)
                .param("brewery", policy.breweryId()).param("maxScore", policy.maxScore())
                .update();
    }
}
