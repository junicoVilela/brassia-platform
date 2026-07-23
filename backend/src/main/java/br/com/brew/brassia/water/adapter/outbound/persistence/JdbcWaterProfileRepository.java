package br.com.brew.brassia.water.adapter.outbound.persistence;

import br.com.brew.brassia.water.application.port.outbound.WaterProfileRepository;
import br.com.brew.brassia.water.domain.IonProfile;
import br.com.brew.brassia.water.domain.WaterProfile;
import br.com.brew.brassia.water.domain.WaterProfileCode;
import br.com.brew.brassia.water.domain.WaterProfileId;
import br.com.brew.brassia.water.domain.WaterProfileName;
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
class JdbcWaterProfileRepository implements WaterProfileRepository {
    private final JdbcClient jdbc;

    JdbcWaterProfileRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean existsByCode(UUID breweryId, String code) {
        return jdbc.sql("SELECT 1 FROM water_profile WHERE brewery_id = :brewery AND code = :code")
                .param("brewery", breweryId).param("code", code)
                .query(Integer.class).optional().isPresent();
    }

    @Override
    public void insert(WaterProfile p) {
        var t = p.targets();
        jdbc.sql("""
                INSERT INTO water_profile (
                    id, brewery_id, code, name, calcium, magnesium, sodium, sulfate, chloride, bicarbonate,
                    active, version, created_at, updated_at)
                VALUES (:id, :brewery, :code, :name, :calcium, :magnesium, :sodium, :sulfate, :chloride,
                        :bicarbonate, :active, :version, :at, :at)
                """)
                .param("id", p.id().value())
                .param("brewery", p.breweryId())
                .param("code", p.code().value())
                .param("name", p.name().value())
                .param("calcium", t.calcium())
                .param("magnesium", t.magnesium())
                .param("sodium", t.sodium())
                .param("sulfate", t.sulfate())
                .param("chloride", t.chloride())
                .param("bicarbonate", t.bicarbonate())
                .param("active", p.active())
                .param("version", p.version())
                .param("at", Timestamp.from(Instant.now()))
                .update();
    }

    @Override
    public boolean update(WaterProfile p, long expectedVersion) {
        var t = p.targets();
        int updated = jdbc.sql("""
                UPDATE water_profile
                SET name = :name, calcium = :calcium, magnesium = :magnesium, sodium = :sodium,
                    sulfate = :sulfate, chloride = :chloride, bicarbonate = :bicarbonate,
                    version = :newVersion, updated_at = :at
                WHERE id = :id AND brewery_id = :brewery AND version = :expected
                """)
                .param("name", p.name().value())
                .param("calcium", t.calcium())
                .param("magnesium", t.magnesium())
                .param("sodium", t.sodium())
                .param("sulfate", t.sulfate())
                .param("chloride", t.chloride())
                .param("bicarbonate", t.bicarbonate())
                .param("newVersion", expectedVersion + 1)
                .param("at", Timestamp.from(Instant.now()))
                .param("id", p.id().value())
                .param("brewery", p.breweryId())
                .param("expected", expectedVersion)
                .update();
        return updated > 0;
    }

    @Override
    public Optional<WaterProfile> findById(UUID breweryId, UUID id) {
        return jdbc.sql("""
                SELECT id, brewery_id, code, name, calcium, magnesium, sodium, sulfate, chloride, bicarbonate,
                       active, version
                FROM water_profile WHERE brewery_id = :brewery AND id = :id
                """)
                .param("brewery", breweryId).param("id", id)
                .query((rs, n) -> map(rs)).optional();
    }

    @Override
    public List<WaterProfile> findPage(UUID breweryId, int page, int size) {
        return jdbc.sql("""
                SELECT id, brewery_id, code, name, calcium, magnesium, sodium, sulfate, chloride, bicarbonate,
                       active, version
                FROM water_profile WHERE brewery_id = :brewery
                ORDER BY code LIMIT :limit OFFSET :offset
                """)
                .param("brewery", breweryId)
                .param("limit", size)
                .param("offset", (long) page * size)
                .query((rs, n) -> map(rs)).list();
    }

    @Override
    public long count(UUID breweryId) {
        return jdbc.sql("SELECT count(*) FROM water_profile WHERE brewery_id = :brewery")
                .param("brewery", breweryId)
                .query(Long.class).single();
    }

    private static WaterProfile map(ResultSet rs) throws SQLException {
        var targets = new IonProfile(
                rs.getBigDecimal("calcium"), rs.getBigDecimal("magnesium"), rs.getBigDecimal("sodium"),
                rs.getBigDecimal("sulfate"), rs.getBigDecimal("chloride"), rs.getBigDecimal("bicarbonate"));
        return WaterProfile.reconstitute(
                new WaterProfileId(rs.getObject("id", UUID.class)),
                rs.getObject("brewery_id", UUID.class),
                new WaterProfileCode(rs.getString("code")),
                new WaterProfileName(rs.getString("name")),
                targets,
                rs.getBoolean("active"),
                rs.getLong("version"));
    }
}
