package br.com.brew.brassia.gas.adapter.outbound.persistence;

import br.com.brew.brassia.gas.application.port.outbound.GasConnectionRepository;
import br.com.brew.brassia.gas.domain.ConnectionStatus;
import br.com.brew.brassia.gas.domain.GasConnection;
import br.com.brew.brassia.gas.domain.LeakTest;
import java.math.BigDecimal;
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
class JdbcGasConnectionRepository implements GasConnectionRepository {

    private static final String COLUMNS = """
            SELECT id, brewery_id, cylinder_id, regulator_id, manifold_id, point_of_use_equipment_id,
                   working_pressure_bar, network_max_pressure_bar, status, connected_at, connected_by,
                   leak_test_passed, leak_test_method, leak_test_drop_bar, leak_test_note, leak_test_by,
                   leak_test_at, disconnected_at, disconnect_reason, version
            FROM gas_connection
            """;

    private final JdbcClient jdbc;

    JdbcGasConnectionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(GasConnection c) {
        jdbc.sql("""
                INSERT INTO gas_connection (id, brewery_id, cylinder_id, regulator_id, manifold_id,
                    point_of_use_equipment_id, working_pressure_bar, network_max_pressure_bar, status,
                    connected_at, connected_by, version)
                VALUES (:id, :brewery, :cylinder, :regulator, :manifold, :point, :working, :networkMax, :status,
                    :at, :by, 0)
                """)
                .param("id", c.id())
                .param("brewery", c.breweryId())
                .param("cylinder", c.cylinderId())
                .param("regulator", c.regulatorId())
                .param("manifold", c.manifoldId())
                .param("point", c.pointOfUseEquipmentId())
                .param("working", c.workingPressureBar())
                .param("networkMax", c.networkMaxPressureBar())
                .param("status", c.status().name())
                .param("at", Timestamp.from(c.connectedAt()))
                .param("by", c.connectedBy())
                .update();
    }

    @Override
    public Optional<GasConnection> findById(UUID breweryId, UUID connectionId) {
        return load(breweryId, connectionId, "");
    }

    @Override
    public Optional<GasConnection> findForUpdate(UUID breweryId, UUID connectionId) {
        return load(breweryId, connectionId, " FOR UPDATE");
    }

    private Optional<GasConnection> load(UUID breweryId, UUID connectionId, String lock) {
        return jdbc.sql(COLUMNS + " WHERE brewery_id = :brewery AND id = :id" + lock)
                .param("brewery", breweryId).param("id", connectionId)
                .query((rs, n) -> map(rs))
                .optional();
    }

    @Override
    public List<GasConnection> findAll(UUID breweryId, boolean onlyOpen) {
        var sql = COLUMNS + " WHERE brewery_id = :brewery"
                + (onlyOpen ? " AND status <> 'DISCONNECTED'" : "") + " ORDER BY connected_at DESC";
        return jdbc.sql(sql).param("brewery", breweryId).query((rs, n) -> map(rs)).list();
    }

    @Override
    public boolean update(GasConnection c, long expectedVersion) {
        var test = c.leakTest();
        return jdbc.sql("""
                UPDATE gas_connection
                SET status = :status, leak_test_passed = :passed, leak_test_method = :method,
                    leak_test_drop_bar = :drop, leak_test_note = :note, leak_test_by = :testedBy,
                    leak_test_at = :testedAt, disconnected_at = :disconnectedAt,
                    disconnect_reason = :disconnectReason, version = version + 1
                WHERE id = :id AND brewery_id = :brewery AND version = :version
                """)
                .param("status", c.status().name())
                .param("passed", test == null ? null : test.passed())
                .param("method", test == null ? null : test.method())
                .param("drop", test == null ? null : test.pressureDropBar())
                .param("note", test == null ? null : test.note())
                .param("testedBy", test == null ? null : test.testedBy())
                .param("testedAt", test == null ? null : Timestamp.from(test.testedAt()))
                .param("disconnectedAt", c.disconnectedAt() == null ? null : Timestamp.from(c.disconnectedAt()))
                .param("disconnectReason", c.disconnectReason())
                .param("id", c.id())
                .param("brewery", c.breweryId())
                .param("version", expectedVersion)
                .update() == 1;
    }

    @Override
    public boolean hasOpenConnectionAtPoint(UUID breweryId, UUID pointOfUseEquipmentId) {
        return jdbc.sql("""
                SELECT 1 FROM gas_connection
                WHERE brewery_id = :brewery AND point_of_use_equipment_id = :point AND status <> 'DISCONNECTED'
                LIMIT 1
                """)
                .param("brewery", breweryId).param("point", pointOfUseEquipmentId)
                .query(Integer.class)
                .optional()
                .isPresent();
    }

    @Override
    public void insertPressureReading(UUID id, UUID breweryId, UUID connectionId, BigDecimal bar, BigDecimal tempC,
            boolean overPressure, UUID actorId, Instant at) {
        jdbc.sql("""
                INSERT INTO gas_pressure_reading (id, brewery_id, connection_id, bar, temp_c, over_pressure,
                    recorded_by, recorded_at)
                VALUES (:id, :brewery, :connection, :bar, :temp, :over, :by, :at)
                """)
                .param("id", id)
                .param("brewery", breweryId)
                .param("connection", connectionId)
                .param("bar", bar)
                .param("temp", tempC)
                .param("over", overPressure)
                .param("by", actorId)
                .param("at", Timestamp.from(at))
                .update();
    }

    @Override
    public List<PressureReadingRow> findPressureReadings(UUID breweryId, UUID connectionId) {
        return jdbc.sql("""
                SELECT id, bar, temp_c, over_pressure, recorded_at FROM gas_pressure_reading
                WHERE brewery_id = :brewery AND connection_id = :connection
                ORDER BY recorded_at DESC
                """)
                .param("brewery", breweryId).param("connection", connectionId)
                .query((rs, n) -> new PressureReadingRow(
                        rs.getObject("id", UUID.class),
                        rs.getBigDecimal("bar"),
                        rs.getBigDecimal("temp_c"),
                        rs.getBoolean("over_pressure"),
                        rs.getTimestamp("recorded_at").toInstant()))
                .list();
    }

    @Override
    public void insertConsumption(UUID id, UUID breweryId, UUID connectionId, UUID cylinderId, BigDecimal kg,
            String reason, UUID actorId, Instant at) {
        jdbc.sql("""
                INSERT INTO gas_consumption (id, brewery_id, connection_id, cylinder_id, kg, reason,
                    recorded_by, recorded_at)
                VALUES (:id, :brewery, :connection, :cylinder, :kg, :reason, :by, :at)
                """)
                .param("id", id)
                .param("brewery", breweryId)
                .param("connection", connectionId)
                .param("cylinder", cylinderId)
                .param("kg", kg)
                .param("reason", reason)
                .param("by", actorId)
                .param("at", Timestamp.from(at))
                .update();
    }

    @Override
    public List<ConsumptionRow> findConsumption(UUID breweryId, UUID connectionId) {
        return jdbc.sql("""
                SELECT id, kg, reason, recorded_at FROM gas_consumption
                WHERE brewery_id = :brewery AND connection_id = :connection
                ORDER BY recorded_at DESC
                """)
                .param("brewery", breweryId).param("connection", connectionId)
                .query((rs, n) -> new ConsumptionRow(
                        rs.getObject("id", UUID.class),
                        rs.getBigDecimal("kg"),
                        rs.getString("reason"),
                        rs.getTimestamp("recorded_at").toInstant()))
                .list();
    }

    private GasConnection map(ResultSet rs) throws SQLException {
        var disconnectedAt = rs.getTimestamp("disconnected_at");
        return GasConnection.reconstitute(
                rs.getObject("id", UUID.class),
                rs.getObject("brewery_id", UUID.class),
                rs.getObject("cylinder_id", UUID.class),
                rs.getObject("regulator_id", UUID.class),
                rs.getObject("manifold_id", UUID.class),
                rs.getObject("point_of_use_equipment_id", UUID.class),
                rs.getBigDecimal("working_pressure_bar"),
                rs.getBigDecimal("network_max_pressure_bar"),
                ConnectionStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("connected_at").toInstant(),
                rs.getObject("connected_by", UUID.class),
                leakTest(rs),
                disconnectedAt == null ? null : disconnectedAt.toInstant(),
                rs.getString("disconnect_reason"),
                rs.getLong("version"));
    }

    private static LeakTest leakTest(ResultSet rs) throws SQLException {
        var passed = rs.getObject("leak_test_passed", Boolean.class);
        if (passed == null) {
            return null;
        }
        return new LeakTest(passed, rs.getString("leak_test_method"), rs.getBigDecimal("leak_test_drop_bar"),
                rs.getString("leak_test_note"), rs.getObject("leak_test_by", UUID.class),
                rs.getTimestamp("leak_test_at").toInstant());
    }
}
