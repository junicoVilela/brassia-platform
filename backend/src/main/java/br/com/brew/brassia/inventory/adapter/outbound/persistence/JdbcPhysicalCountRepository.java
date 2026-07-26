package br.com.brew.brassia.inventory.adapter.outbound.persistence;

import br.com.brew.brassia.inventory.application.port.outbound.PhysicalCountRepository;
import br.com.brew.brassia.inventory.domain.CountLine;
import br.com.brew.brassia.inventory.domain.PhysicalCount;
import br.com.brew.brassia.inventory.domain.PhysicalCountId;
import br.com.brew.brassia.inventory.domain.PhysicalCountStatus;
import br.com.brew.brassia.inventory.domain.StockUnit;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcPhysicalCountRepository implements PhysicalCountRepository {

    private final JdbcClient jdbc;

    JdbcPhysicalCountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(PhysicalCount c, UUID createdBy) {
        jdbc.sql("""
                INSERT INTO physical_count (id, brewery_id, status, created_at, created_by, version)
                VALUES (:id, :brewery, :status, :at, :by, :version)
                """)
                .param("id", c.id().value())
                .param("brewery", c.breweryId())
                .param("status", c.status().name())
                .param("at", Timestamp.from(c.createdAt()))
                .param("by", createdBy)
                .param("version", c.version())
                .update();
        for (var line : c.lines()) {
            jdbc.sql("""
                    INSERT INTO physical_count_line (id, count_id, brewery_id, lot_id, ingredient_id, unit,
                        counted_quantity, system_quantity)
                    VALUES (:id, :count, :brewery, :lot, :ingredient, :unit, :counted, :system)
                    """)
                    .param("id", UUID.randomUUID())
                    .param("count", c.id().value())
                    .param("brewery", c.breweryId())
                    .param("lot", line.lotId())
                    .param("ingredient", line.ingredientId())
                    .param("unit", line.unit().name())
                    .param("counted", line.countedQuantity())
                    .param("system", line.systemQuantity())
                    .update();
        }
    }

    @Override
    public Optional<PhysicalCount> findById(UUID breweryId, UUID countId) {
        var header = jdbc.sql("""
                SELECT id, brewery_id, status, created_at, approved_at, version
                FROM physical_count WHERE brewery_id = :brewery AND id = :id
                """)
                .param("brewery", breweryId).param("id", countId)
                .query((rs, n) -> new Object[] {
                        rs.getObject("id", UUID.class), rs.getString("status"),
                        rs.getTimestamp("created_at"), rs.getTimestamp("approved_at"), rs.getLong("version")})
                .optional();
        if (header.isEmpty()) {
            return Optional.empty();
        }
        var lines = lines(countId);
        var h = header.get();
        var approvedAt = (Timestamp) h[3];
        return Optional.of(PhysicalCount.reconstitute(
                new PhysicalCountId((UUID) h[0]), breweryId, PhysicalCountStatus.valueOf((String) h[1]), lines,
                ((Timestamp) h[2]).toInstant(), approvedAt == null ? null : approvedAt.toInstant(), (long) h[4]));
    }

    @Override
    public List<PhysicalCount> findAll(UUID breweryId) {
        var headers = jdbc.sql("""
                SELECT id, status, created_at, approved_at, version
                FROM physical_count WHERE brewery_id = :brewery ORDER BY created_at DESC
                """)
                .param("brewery", breweryId)
                .query((rs, n) -> new Object[] {
                        rs.getObject("id", UUID.class), rs.getString("status"),
                        rs.getTimestamp("created_at"), rs.getTimestamp("approved_at"), rs.getLong("version")})
                .list();
        return headers.stream().map(h -> {
            var approvedAt = (Timestamp) h[3];
            return PhysicalCount.reconstitute(new PhysicalCountId((UUID) h[0]), breweryId,
                    PhysicalCountStatus.valueOf((String) h[1]), lines((UUID) h[0]),
                    ((Timestamp) h[2]).toInstant(), approvedAt == null ? null : approvedAt.toInstant(), (long) h[4]);
        }).toList();
    }

    @Override
    public boolean markApproved(UUID breweryId, UUID countId, Instant approvedAt, UUID approvedBy) {
        int updated = jdbc.sql("""
                UPDATE physical_count
                SET status = 'APPROVED', approved_at = :at, approved_by = :by, version = version + 1
                WHERE brewery_id = :brewery AND id = :id AND status = 'OPEN'
                """)
                .param("brewery", breweryId).param("id", countId)
                .param("at", Timestamp.from(approvedAt)).param("by", approvedBy)
                .update();
        return updated > 0;
    }

    private List<CountLine> lines(UUID countId) {
        return jdbc.sql("""
                SELECT lot_id, ingredient_id, unit, counted_quantity, system_quantity
                FROM physical_count_line WHERE count_id = :count ORDER BY id
                """)
                .param("count", countId)
                .query((rs, n) -> new CountLine(
                        rs.getObject("lot_id", UUID.class),
                        rs.getObject("ingredient_id", UUID.class),
                        StockUnit.valueOf(rs.getString("unit")),
                        rs.getBigDecimal("counted_quantity"),
                        rs.getBigDecimal("system_quantity")))
                .list();
    }
}
