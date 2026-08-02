package br.com.brew.brassia.packaging.adapter.outbound.persistence;

import br.com.brew.brassia.packaging.application.port.outbound.PackagingRunRepository;
import br.com.brew.brassia.packaging.domain.PackagingRun;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcPackagingRunRepository implements PackagingRunRepository {

    private final JdbcClient jdbc;

    JdbcPackagingRunRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(PackagingRun run) {
        jdbc.sql("""
                INSERT INTO packaging_run (id, plan_id, brewery_id, batch_id, container_volume_ml,
                    input_volume_liters, produced_units, rejected_units, packaged_volume_liters,
                    rejected_volume_liters, losses_liters, note, executed_at, executed_by)
                VALUES (:id, :plan, :brewery, :batch, :containerVolume, :input, :produced, :rejected,
                    :packaged, :rejectedVolume, :losses, :note, :at, :by)
                """)
                .param("id", run.id())
                .param("plan", run.planId())
                .param("brewery", run.breweryId())
                .param("batch", run.batchId())
                .param("containerVolume", run.containerVolumeMl())
                .param("input", run.inputVolumeLiters())
                .param("produced", run.producedUnits())
                .param("rejected", run.rejectedUnits())
                .param("packaged", run.packagedVolumeLiters())
                .param("rejectedVolume", run.rejectedVolumeLiters())
                .param("losses", run.lossesLiters())
                .param("note", run.note())
                .param("at", Timestamp.from(run.executedAt()))
                .param("by", run.executedBy())
                .update();
    }

    @Override
    public Optional<PackagingRun> findByPlan(UUID breweryId, UUID planId) {
        return jdbc.sql("""
                SELECT id, plan_id, brewery_id, batch_id, container_volume_ml, input_volume_liters,
                       produced_units, rejected_units, note, executed_at, executed_by
                FROM packaging_run
                WHERE brewery_id = :brewery AND plan_id = :plan
                """)
                .param("brewery", breweryId).param("plan", planId)
                .query((rs, n) -> map(rs))
                .optional();
    }

    @Override
    public BigDecimal totalInputVolumeOfBatch(UUID breweryId, UUID batchId) {
        return jdbc.sql("""
                SELECT COALESCE(SUM(input_volume_liters), 0) AS total FROM packaging_run
                WHERE brewery_id = :brewery AND batch_id = :batch
                """)
                .param("brewery", breweryId).param("batch", batchId)
                .query(BigDecimal.class)
                .single();
    }

    private PackagingRun map(ResultSet rs) throws SQLException {
        // Volumes derivados não são relidos: o domínio os recalcula das mesmas entradas.
        return PackagingRun.reconstitute(
                rs.getObject("id", UUID.class),
                rs.getObject("plan_id", UUID.class),
                rs.getObject("brewery_id", UUID.class),
                rs.getObject("batch_id", UUID.class),
                rs.getBigDecimal("container_volume_ml"),
                rs.getBigDecimal("input_volume_liters"),
                rs.getInt("produced_units"),
                rs.getInt("rejected_units"),
                rs.getString("note"),
                rs.getTimestamp("executed_at").toInstant(),
                rs.getObject("executed_by", UUID.class));
    }
}
