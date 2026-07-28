package br.com.brew.brassia.sanitation.adapter.outbound.persistence;

import br.com.brew.brassia.sanitation.application.port.outbound.CleaningCycleRepository;
import br.com.brew.brassia.sanitation.domain.CleaningCycle;
import br.com.brew.brassia.sanitation.domain.CleaningCycleStatus;
import br.com.brew.brassia.sanitation.domain.Consumption;
import br.com.brew.brassia.sanitation.domain.ConsumptionSummary;
import br.com.brew.brassia.sanitation.domain.CycleStep;
import br.com.brew.brassia.sanitation.domain.CycleStepStatus;
import br.com.brew.brassia.sanitation.domain.Verification;
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
class JdbcCleaningCycleRepository implements CleaningCycleRepository {

    private static final String COLUMNS = """
            SELECT id, brewery_id, procedure_id, procedure_code, procedure_version, equipment_id, status,
                   interrupt_reason, started_at, ended_at, rinse_ok, visual_ok, atp_rlu, atp_threshold, micro_ok,
                   verified_at, decided_at, water_liters, energy_kwh, product_kg, consumption_at
            FROM sanitation_cleaning_cycle
            """;

    private final JdbcClient jdbc;

    JdbcCleaningCycleRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(CleaningCycle c) {
        var v = c.verification();
        var cons = c.consumption();
        jdbc.sql("""
                INSERT INTO sanitation_cleaning_cycle (id, brewery_id, procedure_id, procedure_code,
                    procedure_version, equipment_id, status, interrupt_reason, started_at, ended_at, rinse_ok,
                    visual_ok, atp_rlu, atp_threshold, micro_ok, verified_at, decided_at, water_liters, energy_kwh,
                    product_kg, consumption_at)
                VALUES (:id, :brewery, :proc, :code, :version, :equipment, :status, :interrupt, :started, :ended,
                    :rinse, :visual, :atp, :atpLimit, :micro, :verified, :decided, :water, :energy, :product,
                    :consumedAt)
                """)
                .param("id", c.id())
                .param("brewery", c.breweryId())
                .param("proc", c.procedureId())
                .param("code", c.procedureCode())
                .param("version", c.procedureVersion())
                .param("equipment", c.equipmentId())
                .param("status", c.status().name())
                .param("interrupt", c.interruptReason())
                .param("started", Timestamp.from(c.startedAt()))
                .param("ended", c.endedAt() == null ? null : Timestamp.from(c.endedAt()))
                .param("rinse", v == null ? null : v.rinseOk())
                .param("visual", v == null ? null : v.visualOk())
                .param("atp", v == null ? null : v.atpRlu())
                .param("atpLimit", v == null ? null : v.atpThreshold())
                .param("micro", v == null ? null : v.microOk())
                .param("verified", v == null ? null : Timestamp.from(v.verifiedAt()))
                .param("decided", c.decidedAt() == null ? null : Timestamp.from(c.decidedAt()))
                .param("water", cons == null ? null : cons.waterLiters())
                .param("energy", cons == null ? null : cons.energyKwh())
                .param("product", cons == null ? null : cons.productKg())
                .param("consumedAt", cons == null ? null : Timestamp.from(cons.recordedAt()))
                .update();
        insertSteps(c);
    }

    private void insertSteps(CleaningCycle c) {
        for (var s : c.steps()) {
            jdbc.sql("""
                    INSERT INTO sanitation_cycle_step (id, cycle_id, brewery_id, step_order, method, product,
                        concentration_min_pct, concentration_max_pct, temp_min_c, temp_max_c, time_minutes,
                        prohibition, evidence_required, status, measured_concentration_pct, measured_temp_c,
                        measured_time_minutes, flow_actual, evidence, out_of_order_reason, overridden,
                        override_reason, executed_at)
                    VALUES (:id, :cycle, :brewery, :seq, :method, :product, :cmin, :cmax, :tmin, :tmax, :time,
                        :prohibition, :evidence_req, :status, :mconc, :mtemp, :mtime, :flow, :evidence, :ooo,
                        :overridden, :override_reason, :executed)
                    """)
                    .param("id", s.id())
                    .param("cycle", c.id())
                    .param("brewery", c.breweryId())
                    .param("seq", s.sequence())
                    .param("method", s.method())
                    .param("product", s.product())
                    .param("cmin", s.concentrationMinPct())
                    .param("cmax", s.concentrationMaxPct())
                    .param("tmin", s.tempMinC())
                    .param("tmax", s.tempMaxC())
                    .param("time", s.timeMinutes())
                    .param("prohibition", s.prohibition())
                    .param("evidence_req", s.evidenceRequired())
                    .param("status", s.status().name())
                    .param("mconc", s.measuredConcentrationPct())
                    .param("mtemp", s.measuredTempC())
                    .param("mtime", s.measuredTimeMinutes())
                    .param("flow", s.flowActual())
                    .param("evidence", s.evidence())
                    .param("ooo", s.outOfOrderReason())
                    .param("overridden", s.overridden())
                    .param("override_reason", s.overrideReason())
                    .param("executed", s.executedAt() == null ? null : Timestamp.from(s.executedAt()))
                    .update();
        }
    }

    @Override
    public Optional<CleaningCycle> findById(UUID breweryId, UUID cycleId) {
        return load(breweryId, cycleId, "");
    }

    @Override
    public Optional<CleaningCycle> findForUpdate(UUID breweryId, UUID cycleId) {
        return load(breweryId, cycleId, " FOR UPDATE");
    }

    private Optional<CleaningCycle> load(UUID breweryId, UUID cycleId, String lock) {
        return jdbc.sql(COLUMNS + " WHERE brewery_id = :brewery AND id = :id" + lock)
                .param("brewery", breweryId).param("id", cycleId)
                .query((rs, n) -> map(rs))
                .optional();
    }

    @Override
    public void update(CleaningCycle c) {
        var v = c.verification();
        var cons = c.consumption();
        jdbc.sql("""
                UPDATE sanitation_cleaning_cycle
                SET status = :status, interrupt_reason = :interrupt, ended_at = :ended, rinse_ok = :rinse,
                    visual_ok = :visual, atp_rlu = :atp, atp_threshold = :atpLimit, micro_ok = :micro,
                    verified_at = :verified, decided_at = :decided, water_liters = :water, energy_kwh = :energy,
                    product_kg = :product, consumption_at = :consumedAt
                WHERE brewery_id = :brewery AND id = :id
                """)
                .param("status", c.status().name())
                .param("interrupt", c.interruptReason())
                .param("ended", c.endedAt() == null ? null : Timestamp.from(c.endedAt()))
                .param("rinse", v == null ? null : v.rinseOk())
                .param("visual", v == null ? null : v.visualOk())
                .param("atp", v == null ? null : v.atpRlu())
                .param("atpLimit", v == null ? null : v.atpThreshold())
                .param("micro", v == null ? null : v.microOk())
                .param("verified", v == null ? null : Timestamp.from(v.verifiedAt()))
                .param("decided", c.decidedAt() == null ? null : Timestamp.from(c.decidedAt()))
                .param("water", cons == null ? null : cons.waterLiters())
                .param("energy", cons == null ? null : cons.energyKwh())
                .param("product", cons == null ? null : cons.productKg())
                .param("consumedAt", cons == null ? null : Timestamp.from(cons.recordedAt()))
                .param("brewery", c.breweryId())
                .param("id", c.id())
                .update();
        jdbc.sql("DELETE FROM sanitation_cycle_step WHERE cycle_id = :cycle").param("cycle", c.id()).update();
        insertSteps(c);
    }

    @Override
    public List<CleaningCycle> findAll(UUID breweryId) {
        return jdbc.sql(COLUMNS + " WHERE brewery_id = :brewery ORDER BY started_at DESC")
                .param("brewery", breweryId)
                .query((rs, n) -> map(rs))
                .list();
    }

    private CleaningCycle map(ResultSet rs) throws SQLException {
        var id = rs.getObject("id", UUID.class);
        var breweryId = rs.getObject("brewery_id", UUID.class);
        var ended = rs.getTimestamp("ended_at");
        var decided = rs.getTimestamp("decided_at");
        return CleaningCycle.reconstitute(
                id,
                breweryId,
                rs.getObject("procedure_id", UUID.class),
                rs.getString("procedure_code"),
                rs.getInt("procedure_version"),
                rs.getObject("equipment_id", UUID.class),
                steps(breweryId, id),
                CleaningCycleStatus.valueOf(rs.getString("status")),
                rs.getString("interrupt_reason"),
                rs.getTimestamp("started_at").toInstant(),
                ended == null ? null : ended.toInstant(),
                verification(rs),
                decided == null ? null : decided.toInstant(),
                consumption(rs));
    }

    private Consumption consumption(ResultSet rs) throws SQLException {
        var consumedAt = rs.getTimestamp("consumption_at");
        if (consumedAt == null) {
            return null;
        }
        return new Consumption(
                rs.getBigDecimal("water_liters"),
                rs.getBigDecimal("energy_kwh"),
                rs.getBigDecimal("product_kg"),
                consumedAt.toInstant());
    }

    @Override
    public ConsumptionSummary summarizeConsumption(UUID breweryId, String procedureCode) {
        return jdbc.sql("""
                SELECT COUNT(*) AS n,
                       AVG(water_liters) AS avg_water, MIN(water_liters) AS min_water, MAX(water_liters) AS max_water,
                       AVG(energy_kwh) AS avg_energy, MIN(energy_kwh) AS min_energy, MAX(energy_kwh) AS max_energy,
                       AVG(product_kg) AS avg_product, MIN(product_kg) AS min_product, MAX(product_kg) AS max_product
                FROM sanitation_cleaning_cycle
                WHERE brewery_id = :brewery AND procedure_code = :code AND consumption_at IS NOT NULL
                """)
                .param("brewery", breweryId).param("code", procedureCode)
                .query((rs, n) -> {
                    int count = rs.getInt("n");
                    if (count == 0) {
                        return ConsumptionSummary.empty(procedureCode);
                    }
                    return new ConsumptionSummary(procedureCode, count,
                            rs.getBigDecimal("avg_water"), rs.getBigDecimal("min_water"), rs.getBigDecimal("max_water"),
                            rs.getBigDecimal("avg_energy"), rs.getBigDecimal("min_energy"), rs.getBigDecimal("max_energy"),
                            rs.getBigDecimal("avg_product"), rs.getBigDecimal("min_product"), rs.getBigDecimal("max_product"));
                })
                .single();
    }

    private Verification verification(ResultSet rs) throws SQLException {
        var verifiedAt = rs.getTimestamp("verified_at");
        if (verifiedAt == null) {
            return null;
        }
        return new Verification(
                rs.getBoolean("rinse_ok"),
                rs.getBoolean("visual_ok"),
                rs.getBigDecimal("atp_rlu"),
                rs.getBigDecimal("atp_threshold"),
                rs.getBoolean("micro_ok"),
                verifiedAt.toInstant());
    }

    private List<CycleStep> steps(UUID breweryId, UUID cycleId) {
        return jdbc.sql("""
                SELECT id, step_order, method, product, concentration_min_pct, concentration_max_pct, temp_min_c,
                       temp_max_c, time_minutes, prohibition, evidence_required, status, measured_concentration_pct,
                       measured_temp_c, measured_time_minutes, flow_actual, evidence, out_of_order_reason,
                       overridden, override_reason, executed_at
                FROM sanitation_cycle_step
                WHERE brewery_id = :brewery AND cycle_id = :cycle ORDER BY step_order
                """)
                .param("brewery", breweryId).param("cycle", cycleId)
                .query((rs, n) -> mapStep(rs))
                .list();
    }

    private CycleStep mapStep(ResultSet rs) throws SQLException {
        var executed = rs.getTimestamp("executed_at");
        return CycleStep.reconstitute(
                rs.getObject("id", UUID.class),
                rs.getInt("step_order"),
                rs.getString("method"),
                rs.getString("product"),
                rs.getBigDecimal("concentration_min_pct"),
                rs.getBigDecimal("concentration_max_pct"),
                rs.getBigDecimal("temp_min_c"),
                rs.getBigDecimal("temp_max_c"),
                rs.getObject("time_minutes", Integer.class),
                rs.getString("prohibition"),
                rs.getBoolean("evidence_required"),
                CycleStepStatus.valueOf(rs.getString("status")),
                rs.getBigDecimal("measured_concentration_pct"),
                rs.getBigDecimal("measured_temp_c"),
                rs.getObject("measured_time_minutes", Integer.class),
                rs.getString("flow_actual"),
                rs.getString("evidence"),
                rs.getString("out_of_order_reason"),
                rs.getBoolean("overridden"),
                rs.getString("override_reason"),
                executed == null ? null : executed.toInstant());
    }
}
