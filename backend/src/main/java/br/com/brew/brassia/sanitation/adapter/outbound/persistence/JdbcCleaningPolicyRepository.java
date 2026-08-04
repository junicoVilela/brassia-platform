package br.com.brew.brassia.sanitation.adapter.outbound.persistence;

import br.com.brew.brassia.sanitation.application.port.outbound.CleaningPolicyRepository;
import br.com.brew.brassia.sanitation.domain.CleaningPolicy;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Linha ausente é resposta válida: significa "sem prazo", que é o comportamento anterior. */
@Repository
class JdbcCleaningPolicyRepository implements CleaningPolicyRepository {

    private final JdbcClient jdbc;

    JdbcCleaningPolicyRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public CleaningPolicy find(UUID breweryId) {
        return jdbc.sql("SELECT brewery_id, validity_hours, version FROM sanitation_cleaning_policy "
                        + "WHERE brewery_id = :brewery")
                .param("brewery", breweryId)
                .query((rs, n) -> CleaningPolicy.reconstitute(rs.getObject("brewery_id", UUID.class),
                        rs.getObject("validity_hours", Integer.class), rs.getLong("version")))
                .optional()
                .orElseGet(() -> CleaningPolicy.none(breweryId));
    }

    @Override
    public void save(CleaningPolicy policy) {
        jdbc.sql("""
                INSERT INTO sanitation_cleaning_policy (brewery_id, validity_hours, version)
                VALUES (:brewery, :hours, 0)
                ON CONFLICT (brewery_id) DO UPDATE
                SET validity_hours = :hours, version = sanitation_cleaning_policy.version + 1
                """)
                .param("brewery", policy.breweryId())
                .param("hours", policy.validityHours().orElse(null))
                .update();
    }
}
