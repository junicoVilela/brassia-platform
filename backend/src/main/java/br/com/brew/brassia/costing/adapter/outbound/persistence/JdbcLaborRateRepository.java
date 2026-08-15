package br.com.brew.brassia.costing.adapter.outbound.persistence;

import br.com.brew.brassia.costing.application.port.outbound.LaborRateRepository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcLaborRateRepository implements LaborRateRepository {

    private final JdbcClient jdbc;

    JdbcLaborRateRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<BigDecimal> find(UUID breweryId) {
        return jdbc.sql("SELECT cost_per_hour FROM costing_labor_rate WHERE brewery_id = :brewery")
                .param("brewery", breweryId)
                .query(BigDecimal.class).optional();
    }

    @Override
    public void save(UUID breweryId, BigDecimal costPerHour, UUID actorId) {
        jdbc.sql("""
                INSERT INTO costing_labor_rate (brewery_id, cost_per_hour, updated_by, updated_at)
                VALUES (:brewery, :rate, :by, :at)
                ON CONFLICT (brewery_id) DO UPDATE
                SET cost_per_hour = :rate, updated_by = :by, updated_at = :at
                """)
                .param("brewery", breweryId).param("rate", costPerHour).param("by", actorId)
                .param("at", Timestamp.from(Instant.now()))
                .update();
    }
}
