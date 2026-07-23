package br.com.brew.brassia.equipment.adapter.outbound.persistence;

import br.com.brew.brassia.equipment.application.port.outbound.EquipmentRepository;
import br.com.brew.brassia.equipment.domain.Equipment;
import br.com.brew.brassia.equipment.domain.EquipmentCode;
import br.com.brew.brassia.equipment.domain.EquipmentId;
import br.com.brew.brassia.equipment.domain.EquipmentName;
import br.com.brew.brassia.equipment.domain.EquipmentSnapshot;
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
class JdbcEquipmentRepository implements EquipmentRepository {
    private final JdbcClient jdbc;

    JdbcEquipmentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean existsByCode(UUID breweryId, String code) {
        return jdbc.sql("SELECT 1 FROM equipment WHERE brewery_id = :brewery AND code = :code")
                .param("brewery", breweryId).param("code", code)
                .query(Integer.class).optional().isPresent();
    }

    @Override
    public void insert(Equipment e) {
        jdbc.sql("""
                INSERT INTO equipment (
                    id, brewery_id, code, name, capacity_liters, dead_space_liters, mash_efficiency_percent,
                    boil_off_liters_per_hour, active, version, created_at, updated_at)
                VALUES (:id, :brewery, :code, :name, :capacity, :dead, :eff, :boil, :active, :version, :at, :at)
                """)
                .param("id", e.id().value())
                .param("brewery", e.breweryId())
                .param("code", e.code().value())
                .param("name", e.name().value())
                .param("capacity", e.capacityLiters())
                .param("dead", e.deadSpaceLiters())
                .param("eff", e.mashEfficiencyPercent())
                .param("boil", e.boilOffLitersPerHour())
                .param("active", e.active())
                .param("version", e.version())
                .param("at", Timestamp.from(Instant.now()))
                .update();
    }

    @Override
    public boolean update(Equipment e, long expectedVersion) {
        int updated = jdbc.sql("""
                UPDATE equipment
                SET name = :name, capacity_liters = :capacity, dead_space_liters = :dead,
                    mash_efficiency_percent = :eff, boil_off_liters_per_hour = :boil,
                    version = :newVersion, updated_at = :at
                WHERE id = :id AND brewery_id = :brewery AND version = :expected
                """)
                .param("name", e.name().value())
                .param("capacity", e.capacityLiters())
                .param("dead", e.deadSpaceLiters())
                .param("eff", e.mashEfficiencyPercent())
                .param("boil", e.boilOffLitersPerHour())
                .param("newVersion", expectedVersion + 1)
                .param("at", Timestamp.from(Instant.now()))
                .param("id", e.id().value())
                .param("brewery", e.breweryId())
                .param("expected", expectedVersion)
                .update();
        return updated > 0;
    }

    @Override
    public void appendRevision(EquipmentSnapshot s, UUID recordedBy) {
        jdbc.sql("""
                INSERT INTO equipment_revision (
                    equipment_id, brewery_id, version, code, name, capacity_liters, dead_space_liters,
                    mash_efficiency_percent, boil_off_liters_per_hour, recorded_at, recorded_by)
                VALUES (:id, :brewery, :version, :code, :name, :capacity, :dead, :eff, :boil, :at, :by)
                ON CONFLICT (equipment_id, version) DO NOTHING
                """)
                .param("id", s.equipmentId())
                .param("brewery", s.breweryId())
                .param("version", s.version())
                .param("code", s.code())
                .param("name", s.name())
                .param("capacity", s.capacityLiters())
                .param("dead", s.deadSpaceLiters())
                .param("eff", s.mashEfficiencyPercent())
                .param("boil", s.boilOffLitersPerHour())
                .param("at", Timestamp.from(Instant.now()))
                .param("by", recordedBy)
                .update();
    }

    @Override
    public Optional<Equipment> findById(UUID breweryId, UUID id) {
        return jdbc.sql("""
                SELECT id, brewery_id, code, name, capacity_liters, dead_space_liters, mash_efficiency_percent,
                       boil_off_liters_per_hour, active, version
                FROM equipment WHERE brewery_id = :brewery AND id = :id
                """)
                .param("brewery", breweryId).param("id", id)
                .query((rs, n) -> map(rs)).optional();
    }

    @Override
    public Optional<EquipmentSnapshot> findRevision(UUID breweryId, UUID id, long version) {
        return jdbc.sql("""
                SELECT equipment_id, brewery_id, version, code, name, capacity_liters, dead_space_liters,
                       mash_efficiency_percent, boil_off_liters_per_hour
                FROM equipment_revision
                WHERE brewery_id = :brewery AND equipment_id = :id AND version = :version
                """)
                .param("brewery", breweryId).param("id", id).param("version", version)
                .query((rs, n) -> new EquipmentSnapshot(
                        rs.getObject("equipment_id", UUID.class),
                        rs.getObject("brewery_id", UUID.class),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getBigDecimal("capacity_liters"),
                        rs.getBigDecimal("dead_space_liters"),
                        rs.getBigDecimal("mash_efficiency_percent"),
                        rs.getBigDecimal("boil_off_liters_per_hour"),
                        rs.getLong("version")))
                .optional();
    }

    @Override
    public List<Equipment> findPage(UUID breweryId, int page, int size) {
        return jdbc.sql("""
                SELECT id, brewery_id, code, name, capacity_liters, dead_space_liters, mash_efficiency_percent,
                       boil_off_liters_per_hour, active, version
                FROM equipment WHERE brewery_id = :brewery
                ORDER BY code LIMIT :limit OFFSET :offset
                """)
                .param("brewery", breweryId)
                .param("limit", size)
                .param("offset", (long) page * size)
                .query((rs, n) -> map(rs)).list();
    }

    @Override
    public long count(UUID breweryId) {
        return jdbc.sql("SELECT count(*) FROM equipment WHERE brewery_id = :brewery")
                .param("brewery", breweryId)
                .query(Long.class).single();
    }

    private static Equipment map(ResultSet rs) throws SQLException {
        return Equipment.reconstitute(
                new EquipmentId(rs.getObject("id", UUID.class)),
                rs.getObject("brewery_id", UUID.class),
                new EquipmentCode(rs.getString("code")),
                new EquipmentName(rs.getString("name")),
                rs.getBigDecimal("capacity_liters"),
                rs.getBigDecimal("dead_space_liters"),
                rs.getBigDecimal("mash_efficiency_percent"),
                rs.getBigDecimal("boil_off_liters_per_hour"),
                rs.getBoolean("active"),
                rs.getLong("version"));
    }
}
