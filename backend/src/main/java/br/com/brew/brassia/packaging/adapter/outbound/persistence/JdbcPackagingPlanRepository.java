package br.com.brew.brassia.packaging.adapter.outbound.persistence;

import br.com.brew.brassia.packaging.application.port.outbound.PackagingPlanRepository;
import br.com.brew.brassia.packaging.domain.ChecklistItem;
import br.com.brew.brassia.packaging.domain.PackagingPlan;
import br.com.brew.brassia.packaging.domain.PackagingPlanStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcPackagingPlanRepository implements PackagingPlanRepository {

    private static final String COLUMNS = """
            SELECT id, brewery_id, code, batch_id, container_id, container_volume_ml, planned_units,
                   line_equipment_id, planned_start, planned_end, status, reserved_at, reserved_by,
                   cancel_reason, cancelled_at, version
            FROM packaging_plan
            """;

    private final JdbcClient jdbc;

    JdbcPackagingPlanRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(PackagingPlan plan) {
        jdbc.sql("""
                INSERT INTO packaging_plan (id, brewery_id, code, batch_id, container_id, container_volume_ml,
                    planned_units, planned_volume_liters, line_equipment_id, planned_start, planned_end, status,
                    version)
                VALUES (:id, :brewery, :code, :batch, :container, :containerVolume, :units, :volume, :line,
                    :start, :end, :status, 0)
                """)
                .param("id", plan.id())
                .param("brewery", plan.breweryId())
                .param("code", plan.code())
                .param("batch", plan.batchId())
                .param("container", plan.containerId())
                .param("containerVolume", plan.containerVolumeMl())
                .param("units", plan.plannedUnits())
                .param("volume", plan.plannedVolumeLiters())
                .param("line", plan.lineEquipmentId())
                .param("start", Timestamp.from(plan.plannedStart()))
                .param("end", Timestamp.from(plan.plannedEnd()))
                .param("status", plan.status().name())
                .update();
    }

    @Override
    public Optional<PackagingPlan> findById(UUID breweryId, UUID planId) {
        return load(breweryId, planId, "");
    }

    @Override
    public Optional<PackagingPlan> findForUpdate(UUID breweryId, UUID planId) {
        return load(breweryId, planId, " FOR UPDATE");
    }

    private Optional<PackagingPlan> load(UUID breweryId, UUID planId, String lock) {
        // Checklist antes do plano: consultar dentro do mapeamento aninharia queries na mesma conexão.
        var checklist = checklistOf(breweryId, planId);
        return jdbc.sql(COLUMNS + " WHERE brewery_id = :brewery AND id = :id" + lock)
                .param("brewery", breweryId).param("id", planId)
                .query((rs, n) -> map(rs, checklist))
                .optional();
    }

    @Override
    public List<PackagingPlan> findAll(UUID breweryId, UUID batchId) {
        var checklists = checklistsOf(breweryId, batchId);
        var sql = COLUMNS + " WHERE brewery_id = :brewery"
                + (batchId == null ? "" : " AND batch_id = :batch") + " ORDER BY planned_start DESC";
        var statement = jdbc.sql(sql).param("brewery", breweryId);
        if (batchId != null) {
            statement = statement.param("batch", batchId);
        }
        return statement.query((rs, n) -> map(rs, checklists.getOrDefault(
                rs.getObject("id", UUID.class), Map.of()))).list();
    }

    @Override
    public boolean existsByCode(UUID breweryId, String code) {
        return jdbc.sql("SELECT 1 FROM packaging_plan WHERE brewery_id = :brewery AND code = :code")
                .param("brewery", breweryId).param("code", code)
                .query(Integer.class)
                .optional()
                .isPresent();
    }

    @Override
    public boolean confirmChecklistItem(UUID breweryId, UUID planId, ChecklistItem item, UUID actorId, Instant at) {
        // Guardado pelo estado (só PLANNED) e pela PK: repetir preserva a primeira evidência.
        return jdbc.sql("""
                INSERT INTO packaging_plan_checklist_item (plan_id, brewery_id, item, confirmed_by, confirmed_at)
                SELECT p.id, p.brewery_id, :item, :by, :at
                FROM packaging_plan p
                WHERE p.id = :plan AND p.brewery_id = :brewery AND p.status = 'PLANNED'
                ON CONFLICT (plan_id, item) DO NOTHING
                """)
                .param("plan", planId)
                .param("brewery", breweryId)
                .param("item", item.name())
                .param("by", actorId)
                .param("at", Timestamp.from(at))
                .update() > 0;
    }

    @Override
    public boolean updateStatus(PackagingPlan plan, long expectedVersion) {
        return jdbc.sql("""
                UPDATE packaging_plan
                SET status = :status, reserved_at = :reservedAt, reserved_by = :reservedBy,
                    cancel_reason = :reason, cancelled_at = :cancelledAt, version = version + 1
                WHERE id = :id AND brewery_id = :brewery AND version = :version
                """)
                .param("status", plan.status().name())
                .param("reservedAt", plan.reservedAt() == null ? null : Timestamp.from(plan.reservedAt()))
                .param("reservedBy", plan.reservedBy())
                .param("reason", plan.cancelReason())
                .param("cancelledAt", plan.cancelledAt() == null ? null : Timestamp.from(plan.cancelledAt()))
                .param("id", plan.id())
                .param("brewery", plan.breweryId())
                .param("version", expectedVersion)
                .update() == 1;
    }

    @Override
    public boolean hasLineConflict(UUID breweryId, UUID lineEquipmentId, Instant from, Instant to,
            UUID excludePlanId) {
        // Sobreposição de intervalos semiabertos [start, end): plano cancelado não ocupa linha.
        return jdbc.sql("""
                SELECT 1 FROM packaging_plan
                WHERE brewery_id = :brewery AND line_equipment_id = :line AND id <> :exclude
                  AND status <> 'CANCELLED'
                  AND planned_start < :end AND planned_end > :start
                LIMIT 1
                """)
                .param("brewery", breweryId).param("line", lineEquipmentId).param("exclude", excludePlanId)
                .param("start", Timestamp.from(from)).param("end", Timestamp.from(to))
                .query(Integer.class)
                .optional()
                .isPresent();
    }

    @Override
    public Optional<Instant> lastLineUse(UUID breweryId, UUID lineEquipmentId, Instant before, UUID excludePlanId) {
        return jdbc.sql("""
                SELECT MAX(planned_start) AS last_use FROM packaging_plan
                WHERE brewery_id = :brewery AND line_equipment_id = :line AND id <> :exclude
                  AND status IN ('RESERVED', 'EXECUTED') AND planned_start < :before
                """)
                .param("brewery", breweryId).param("line", lineEquipmentId).param("exclude", excludePlanId)
                .param("before", Timestamp.from(before))
                .query((rs, n) -> rs.getTimestamp("last_use"))
                .optional()
                .filter(java.util.Objects::nonNull)
                .map(Timestamp::toInstant);
    }

    private Map<ChecklistItem, PackagingPlan.Confirmation> checklistOf(UUID breweryId, UUID planId) {
        var checklist = new EnumMap<ChecklistItem, PackagingPlan.Confirmation>(ChecklistItem.class);
        jdbc.sql("""
                SELECT item, confirmed_by, confirmed_at FROM packaging_plan_checklist_item
                WHERE brewery_id = :brewery AND plan_id = :plan
                """)
                .param("brewery", breweryId).param("plan", planId)
                .query((rs, n) -> checklist.put(
                        ChecklistItem.valueOf(rs.getString("item")), confirmation(rs)))
                .list();
        return checklist;
    }

    /** Checklists de todos os planos da consulta numa query só (evita N+1 na listagem). */
    private Map<UUID, Map<ChecklistItem, PackagingPlan.Confirmation>> checklistsOf(UUID breweryId, UUID batchId) {
        var byPlan = new HashMap<UUID, Map<ChecklistItem, PackagingPlan.Confirmation>>();
        var sql = """
                SELECT c.plan_id, c.item, c.confirmed_by, c.confirmed_at
                FROM packaging_plan_checklist_item c
                JOIN packaging_plan p ON p.id = c.plan_id
                WHERE c.brewery_id = :brewery
                """ + (batchId == null ? "" : " AND p.batch_id = :batch");
        var statement = jdbc.sql(sql).param("brewery", breweryId);
        if (batchId != null) {
            statement = statement.param("batch", batchId);
        }
        statement.query((rs, n) -> byPlan
                .computeIfAbsent(rs.getObject("plan_id", UUID.class), k -> new EnumMap<>(ChecklistItem.class))
                .put(ChecklistItem.valueOf(rs.getString("item")), confirmation(rs)))
                .list();
        return byPlan;
    }

    private static PackagingPlan.Confirmation confirmation(ResultSet rs) throws SQLException {
        return new PackagingPlan.Confirmation(
                rs.getObject("confirmed_by", UUID.class),
                rs.getTimestamp("confirmed_at").toInstant());
    }

    private PackagingPlan map(ResultSet rs, Map<ChecklistItem, PackagingPlan.Confirmation> checklist)
            throws SQLException {
        var reservedAt = rs.getTimestamp("reserved_at");
        var cancelledAt = rs.getTimestamp("cancelled_at");
        return PackagingPlan.reconstitute(
                rs.getObject("id", UUID.class),
                rs.getObject("brewery_id", UUID.class),
                rs.getString("code"),
                rs.getObject("batch_id", UUID.class),
                rs.getObject("container_id", UUID.class),
                rs.getBigDecimal("container_volume_ml"),
                rs.getInt("planned_units"),
                rs.getObject("line_equipment_id", UUID.class),
                rs.getTimestamp("planned_start").toInstant(),
                rs.getTimestamp("planned_end").toInstant(),
                PackagingPlanStatus.valueOf(rs.getString("status")),
                checklist,
                reservedAt == null ? null : reservedAt.toInstant(),
                rs.getObject("reserved_by", UUID.class),
                rs.getString("cancel_reason"),
                cancelledAt == null ? null : cancelledAt.toInstant(),
                rs.getLong("version"));
    }
}
