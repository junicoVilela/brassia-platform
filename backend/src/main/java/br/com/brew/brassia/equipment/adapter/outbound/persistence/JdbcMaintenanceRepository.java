package br.com.brew.brassia.equipment.adapter.outbound.persistence;

import br.com.brew.brassia.equipment.application.port.outbound.MaintenanceRepository;
import br.com.brew.brassia.equipment.domain.EquipmentMaintenance;
import br.com.brew.brassia.equipment.domain.MaintenanceId;
import br.com.brew.brassia.equipment.domain.MaintenanceKind;
import br.com.brew.brassia.equipment.domain.MaintenanceStatus;
import br.com.brew.brassia.equipment.domain.TimeRange;
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
class JdbcMaintenanceRepository implements MaintenanceRepository {
    private final JdbcClient jdbc;

    JdbcMaintenanceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(EquipmentMaintenance m) {
        jdbc.sql("""
                INSERT INTO equipment_maintenance (
                    id, brewery_id, equipment_id, kind, instrument, starts_at, ends_at, notes, status, version,
                    created_at)
                VALUES (:id, :brewery, :equipment, :kind, :instrument, :startsAt, :endsAt, :notes, :status,
                        :version, :at)
                """)
                .param("id", m.id().value())
                .param("brewery", m.breweryId())
                .param("equipment", m.equipmentId())
                .param("kind", m.kind().name())
                .param("instrument", m.instrument())
                .param("startsAt", Timestamp.from(m.range().startAt()))
                .param("endsAt", Timestamp.from(m.range().endAt()))
                .param("notes", m.notes())
                .param("status", m.status().name())
                .param("version", m.version())
                .param("at", Timestamp.from(Instant.now()))
                .update();
    }

    @Override
    public boolean hasScheduledOverlap(UUID breweryId, UUID equipmentId, Instant from, Instant to) {
        return jdbc.sql("""
                SELECT 1 FROM equipment_maintenance
                WHERE brewery_id = :brewery AND equipment_id = :equipment AND status = 'SCHEDULED'
                  AND starts_at < :to AND ends_at > :from
                LIMIT 1
                """)
                .param("brewery", breweryId)
                .param("equipment", equipmentId)
                .param("from", Timestamp.from(from))
                .param("to", Timestamp.from(to))
                .query(Integer.class).optional().isPresent();
    }

    @Override
    public Optional<EquipmentMaintenance> findById(UUID breweryId, UUID equipmentId, UUID id) {
        return jdbc.sql("""
                SELECT id, brewery_id, equipment_id, kind, instrument, starts_at, ends_at, notes, status, version
                FROM equipment_maintenance
                WHERE brewery_id = :brewery AND equipment_id = :equipment AND id = :id
                """)
                .param("brewery", breweryId).param("equipment", equipmentId).param("id", id)
                .query((rs, n) -> map(rs)).optional();
    }

    @Override
    public boolean updateStatus(EquipmentMaintenance m, long expectedVersion) {
        int updated = jdbc.sql("""
                UPDATE equipment_maintenance
                SET status = :status, version = :newVersion
                WHERE id = :id AND brewery_id = :brewery AND equipment_id = :equipment AND version = :expected
                """)
                .param("status", m.status().name())
                .param("newVersion", expectedVersion + 1)
                .param("id", m.id().value())
                .param("brewery", m.breweryId())
                .param("equipment", m.equipmentId())
                .param("expected", expectedVersion)
                .update();
        return updated > 0;
    }

    @Override
    public List<EquipmentMaintenance> findByEquipment(UUID breweryId, UUID equipmentId) {
        return jdbc.sql("""
                SELECT id, brewery_id, equipment_id, kind, instrument, starts_at, ends_at, notes, status, version
                FROM equipment_maintenance
                WHERE brewery_id = :brewery AND equipment_id = :equipment
                ORDER BY starts_at
                """)
                .param("brewery", breweryId).param("equipment", equipmentId)
                .query((rs, n) -> map(rs)).list();
    }

    private static EquipmentMaintenance map(ResultSet rs) throws SQLException {
        return EquipmentMaintenance.reconstitute(
                new MaintenanceId(rs.getObject("id", UUID.class)),
                rs.getObject("brewery_id", UUID.class),
                rs.getObject("equipment_id", UUID.class),
                MaintenanceKind.valueOf(rs.getString("kind")),
                rs.getString("instrument"),
                new TimeRange(rs.getTimestamp("starts_at").toInstant(), rs.getTimestamp("ends_at").toInstant()),
                rs.getString("notes"),
                MaintenanceStatus.valueOf(rs.getString("status")),
                rs.getLong("version"));
    }
}
