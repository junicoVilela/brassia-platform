package br.com.brew.brassia.fermentation.adapter.outbound.persistence;

import br.com.brew.brassia.fermentation.application.port.outbound.YeastPolicyRepository;
import br.com.brew.brassia.fermentation.domain.YeastPolicy;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcYeastPolicyRepository implements YeastPolicyRepository {

    private final JdbcClient jdbc;

    JdbcYeastPolicyRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<YeastPolicy> find(UUID breweryId) {
        return jdbc.sql("""
                SELECT max_generation, max_age_days, min_viability_percent
                FROM fermentation_yeast_policy WHERE brewery_id = :brewery
                """)
                .param("brewery", breweryId)
                .query((rs, n) -> new YeastPolicy(
                        rs.getInt("max_generation"),
                        rs.getInt("max_age_days"),
                        rs.getBigDecimal("min_viability_percent")))
                .optional();
    }

    @Override
    public void save(UUID breweryId, YeastPolicy policy) {
        jdbc.sql("""
                INSERT INTO fermentation_yeast_policy (brewery_id, max_generation, max_age_days,
                    min_viability_percent)
                VALUES (:brewery, :generation, :age, :viability)
                ON CONFLICT (brewery_id) DO UPDATE
                SET max_generation = :generation, max_age_days = :age, min_viability_percent = :viability
                """)
                .param("brewery", breweryId)
                .param("generation", policy.maxGeneration())
                .param("age", policy.maxAgeDays())
                .param("viability", policy.minViabilityPercent())
                .update();
    }
}
