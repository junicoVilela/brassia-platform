package br.com.brew.brassia.sensor.adapter.outbound.persistence;

import br.com.brew.brassia.sensor.application.port.outbound.DeviceRepository;
import br.com.brew.brassia.sensor.domain.DeviceStatus;
import br.com.brew.brassia.sensor.domain.Measure;
import br.com.brew.brassia.sensor.domain.SensorDevice;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Dispositivos em PostgreSQL (INT-001). */
@Repository
class JdbcSensorDeviceRepository implements DeviceRepository {

    private static final String COLUMNS = """
            id, brewery_id, code, name, measure, unit, equipment_id, expected_interval_seconds,
            status, registered_by, registered_at, version
            """;

    private final JdbcClient jdbc;

    JdbcSensorDeviceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(SensorDevice device) {
        jdbc.sql("""
                INSERT INTO sensor_device (id, brewery_id, code, name, measure, unit, equipment_id,
                        expected_interval_seconds, status, registered_by, registered_at, version)
                VALUES (:id, :brewery, :code, :name, :measure, :unit, :equipment, :interval, :status,
                        :by, :at, :version)
                """)
                .param("id", device.id())
                .param("brewery", device.breweryId())
                .param("code", device.code())
                .param("name", device.name())
                .param("measure", device.measure().name())
                .param("unit", device.unit())
                .param("equipment", device.equipmentId())
                .param("interval", device.expectedInterval() == null
                        ? null : (int) device.expectedInterval().toSeconds())
                .param("status", device.status().name())
                .param("by", device.registeredBy())
                .param("at", Timestamp.from(device.registeredAt()))
                .param("version", device.version())
                .update();
    }

    /**
     * Só o estado, e só quando a versão bate.
     *
     * <p>O {@code UPDATE} condicional é o bloqueio otimista inteiro: se a linha não for atingida, alguém
     * mexeu no dispositivo entre a leitura e a escrita. Nenhuma outra coluna aparece no SET — grandeza,
     * unidade e código de um dispositivo cadastrado não mudam, e aqui a alteração deles não é uma
     * possibilidade que dependa de disciplina.
     */
    @Override
    public boolean updateStatus(SensorDevice device, long expectedVersion) {
        return jdbc.sql("""
                UPDATE sensor_device SET status = :status, version = version + 1
                WHERE id = :id AND brewery_id = :brewery AND version = :expected
                """)
                .param("status", device.status().name())
                .param("id", device.id())
                .param("brewery", device.breweryId())
                .param("expected", expectedVersion)
                .update() == 1;
    }

    @Override
    public Optional<SensorDevice> byCode(UUID breweryId, String code) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM sensor_device "
                        + "WHERE brewery_id = :brewery AND code = :code")
                .param("brewery", breweryId).param("code", code)
                .query(this::map).optional();
    }

    @Override
    public Optional<SensorDevice> byId(UUID breweryId, UUID deviceId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM sensor_device "
                        + "WHERE brewery_id = :brewery AND id = :id")
                .param("brewery", breweryId).param("id", deviceId)
                .query(this::map).optional();
    }

    @Override
    public List<SensorDevice> findAll(UUID breweryId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM sensor_device "
                        + "WHERE brewery_id = :brewery ORDER BY code")
                .param("brewery", breweryId)
                .query(this::map).list();
    }

    private SensorDevice map(ResultSet rs, int rowNum) throws SQLException {
        var interval = rs.getObject("expected_interval_seconds", Integer.class);
        return SensorDevice.reconstitute(
                rs.getObject("id", UUID.class),
                rs.getObject("brewery_id", UUID.class),
                rs.getString("code"),
                rs.getString("name"),
                Measure.valueOf(rs.getString("measure")),
                rs.getString("unit"),
                rs.getObject("equipment_id", UUID.class),
                interval == null ? null : Duration.ofSeconds(interval),
                DeviceStatus.valueOf(rs.getString("status")),
                rs.getObject("registered_by", UUID.class),
                rs.getTimestamp("registered_at").toInstant(),
                rs.getLong("version"));
    }
}
