package br.com.brew.brassia.water.adapter.outbound.persistence;

import br.com.brew.brassia.water.application.port.outbound.WaterSourceRepository;
import br.com.brew.brassia.water.domain.WaterSource;
import br.com.brew.brassia.water.domain.WaterSourceCode;
import br.com.brew.brassia.water.domain.WaterSourceId;
import br.com.brew.brassia.water.domain.WaterSourceName;
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
class JdbcWaterSourceRepository implements WaterSourceRepository {
    private final JdbcClient jdbc;

    JdbcWaterSourceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean existsByCode(UUID breweryId, String code) {
        return jdbc.sql("SELECT 1 FROM water_source WHERE brewery_id = :brewery AND code = :code")
                .param("brewery", breweryId).param("code", code)
                .query(Integer.class).optional().isPresent();
    }

    @Override
    public void insert(WaterSource s) {
        jdbc.sql("""
                INSERT INTO water_source (id, brewery_id, code, name, active, version, created_at, updated_at)
                VALUES (:id, :brewery, :code, :name, :active, :version, :at, :at)
                """)
                .param("id", s.id().value())
                .param("brewery", s.breweryId())
                .param("code", s.code().value())
                .param("name", s.name().value())
                .param("active", s.active())
                .param("version", s.version())
                .param("at", Timestamp.from(Instant.now()))
                .update();
    }

    @Override
    public boolean update(WaterSource s, long expectedVersion) {
        int updated = jdbc.sql("""
                UPDATE water_source
                SET name = :name, version = :newVersion, updated_at = :at
                WHERE id = :id AND brewery_id = :brewery AND version = :expected
                """)
                .param("name", s.name().value())
                .param("newVersion", expectedVersion + 1)
                .param("at", Timestamp.from(Instant.now()))
                .param("id", s.id().value())
                .param("brewery", s.breweryId())
                .param("expected", expectedVersion)
                .update();
        return updated > 0;
    }

    @Override
    public Optional<WaterSource> findById(UUID breweryId, UUID id) {
        return jdbc.sql("""
                SELECT id, brewery_id, code, name, active, version
                FROM water_source WHERE brewery_id = :brewery AND id = :id
                """)
                .param("brewery", breweryId).param("id", id)
                .query((rs, n) -> map(rs)).optional();
    }

    @Override
    public List<WaterSource> findPage(UUID breweryId, int page, int size) {
        return jdbc.sql("""
                SELECT id, brewery_id, code, name, active, version
                FROM water_source WHERE brewery_id = :brewery
                ORDER BY code LIMIT :limit OFFSET :offset
                """)
                .param("brewery", breweryId)
                .param("limit", size)
                .param("offset", (long) page * size)
                .query((rs, n) -> map(rs)).list();
    }

    @Override
    public long count(UUID breweryId) {
        return jdbc.sql("SELECT count(*) FROM water_source WHERE brewery_id = :brewery")
                .param("brewery", breweryId)
                .query(Long.class).single();
    }

    private static WaterSource map(ResultSet rs) throws SQLException {
        return WaterSource.reconstitute(
                new WaterSourceId(rs.getObject("id", UUID.class)),
                rs.getObject("brewery_id", UUID.class),
                new WaterSourceCode(rs.getString("code")),
                new WaterSourceName(rs.getString("name")),
                rs.getBoolean("active"),
                rs.getLong("version"));
    }
}
