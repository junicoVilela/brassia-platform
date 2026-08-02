package br.com.brew.brassia.gas.adapter.outbound.persistence;

import br.com.brew.brassia.gas.application.port.outbound.GasCylinderRepository;
import br.com.brew.brassia.gas.domain.CylinderStatus;
import br.com.brew.brassia.gas.domain.GasCylinder;
import br.com.brew.brassia.gas.domain.GasType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcGasCylinderRepository implements GasCylinderRepository {

    private static final String COLUMNS = """
            SELECT id, brewery_id, code, gas_type, capacity_kg, tare_kg, content_kg, requalification_due_on,
                   status, block_reason, location, version
            FROM gas_cylinder
            """;

    private final JdbcClient jdbc;

    JdbcGasCylinderRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(GasCylinder c) {
        jdbc.sql("""
                INSERT INTO gas_cylinder (id, brewery_id, code, gas_type, capacity_kg, tare_kg, content_kg,
                    requalification_due_on, status, block_reason, location, version)
                VALUES (:id, :brewery, :code, :gasType, :capacity, :tare, :content, :dueOn, :status, :reason,
                    :location, 0)
                """)
                .param("id", c.id())
                .param("brewery", c.breweryId())
                .param("code", c.code())
                .param("gasType", c.gasType().name())
                .param("capacity", c.capacityKg())
                .param("tare", c.tareKg())
                .param("content", c.contentKg())
                .param("dueOn", c.requalificationDueOn())
                .param("status", c.status().name())
                .param("reason", c.blockReason())
                .param("location", c.location())
                .update();
    }

    @Override
    public Optional<GasCylinder> findById(UUID breweryId, UUID cylinderId) {
        return load(breweryId, cylinderId, "");
    }

    @Override
    public Optional<GasCylinder> findForUpdate(UUID breweryId, UUID cylinderId) {
        return load(breweryId, cylinderId, " FOR UPDATE");
    }

    private Optional<GasCylinder> load(UUID breweryId, UUID cylinderId, String lock) {
        return jdbc.sql(COLUMNS + " WHERE brewery_id = :brewery AND id = :id" + lock)
                .param("brewery", breweryId).param("id", cylinderId)
                .query((rs, n) -> map(rs))
                .optional();
    }

    @Override
    public List<GasCylinder> findAll(UUID breweryId) {
        return jdbc.sql(COLUMNS + " WHERE brewery_id = :brewery ORDER BY code")
                .param("brewery", breweryId)
                .query((rs, n) -> map(rs))
                .list();
    }

    @Override
    public boolean existsByCode(UUID breweryId, String code) {
        return jdbc.sql("SELECT 1 FROM gas_cylinder WHERE brewery_id = :brewery AND code = :code")
                .param("brewery", breweryId).param("code", code)
                .query(Integer.class)
                .optional()
                .isPresent();
    }

    @Override
    public boolean update(GasCylinder c, long expectedVersion) {
        return jdbc.sql("""
                UPDATE gas_cylinder
                SET content_kg = :content, requalification_due_on = :dueOn, status = :status,
                    block_reason = :reason, location = :location, version = version + 1
                WHERE id = :id AND brewery_id = :brewery AND version = :version
                """)
                .param("content", c.contentKg())
                .param("dueOn", c.requalificationDueOn())
                .param("status", c.status().name())
                .param("reason", c.blockReason())
                .param("location", c.location())
                .param("id", c.id())
                .param("brewery", c.breweryId())
                .param("version", expectedVersion)
                .update() == 1;
    }

    private GasCylinder map(ResultSet rs) throws SQLException {
        return GasCylinder.reconstitute(
                rs.getObject("id", UUID.class),
                rs.getObject("brewery_id", UUID.class),
                rs.getString("code"),
                GasType.valueOf(rs.getString("gas_type")),
                rs.getBigDecimal("capacity_kg"),
                rs.getBigDecimal("tare_kg"),
                rs.getBigDecimal("content_kg"),
                rs.getObject("requalification_due_on", java.time.LocalDate.class),
                CylinderStatus.valueOf(rs.getString("status")),
                rs.getString("block_reason"),
                rs.getString("location"),
                rs.getLong("version"));
    }
}
