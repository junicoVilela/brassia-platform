package br.com.brew.brassia.crm.adapter.outbound.persistence;

import br.com.brew.brassia.crm.application.port.outbound.RetentionPolicyRepository;
import br.com.brew.brassia.crm.domain.RetentionPolicy;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcRetentionPolicyRepository implements RetentionPolicyRepository {

    private final JdbcClient jdbc;

    JdbcRetentionPolicyRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public RetentionPolicy find(UUID breweryId) {
        // Ausência de linha vira RetentionPolicy.none, e não Optional.empty: "a cervejaria não decidiu"
        // é um estado da política. Assim o comportamento seguro — nada expira — é o padrão, em vez de
        // depender de quem chama lembrar de tratar o vazio.
        return jdbc.sql("SELECT days_after_last_interaction FROM crm_retention_policy "
                + "WHERE brewery_id = :brewery")
                .param("brewery", breweryId)
                .query(Integer.class).optional()
                .map(days -> RetentionPolicy.of(breweryId, days))
                .orElseGet(() -> RetentionPolicy.none(breweryId));
    }

    @Override
    public void save(UUID breweryId, int daysAfterLastInteraction, UUID actorId) {
        jdbc.sql("""
                INSERT INTO crm_retention_policy (brewery_id, days_after_last_interaction, updated_by,
                                                  updated_at)
                VALUES (:brewery, :days, :by, :at)
                ON CONFLICT (brewery_id) DO UPDATE
                SET days_after_last_interaction = :days, updated_by = :by, updated_at = :at
                """)
                .param("brewery", breweryId).param("days", daysAfterLastInteraction).param("by", actorId)
                .param("at", Timestamp.from(Instant.now()))
                .update();
    }
}
