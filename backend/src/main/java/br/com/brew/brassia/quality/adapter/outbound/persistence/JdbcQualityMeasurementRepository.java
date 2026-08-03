package br.com.brew.brassia.quality.adapter.outbound.persistence;

import br.com.brew.brassia.quality.application.port.outbound.MeasurementRepository;
import br.com.brew.brassia.quality.domain.Deviation;
import br.com.brew.brassia.quality.domain.DeviationStatus;
import br.com.brew.brassia.quality.domain.Measurement;
import br.com.brew.brassia.quality.domain.Severity;
import br.com.brew.brassia.quality.domain.SpecLimits;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Medição e desvio só entram — não há reescrita: o veredito de uma medição é histórico.
 *
 * <p>O prefixo {@code Quality} no nome não é enfeite: o scan de componentes do Spring é global por
 * nome simples de classe, e {@code production} já tem um {@code JdbcMeasurementRepository}.
 */
@Repository
class JdbcQualityMeasurementRepository implements MeasurementRepository {

    private static final String DEVIATION_COLUMNS = """
            SELECT id, brewery_id, measurement_id, plan_id, point_id, parameter, severity, bound,
                   limit_value, measured_value, unit, action, status, opened_at, opened_by
            FROM quality_deviation
            """;

    private final JdbcClient jdbc;

    JdbcQualityMeasurementRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Measurement m) {
        jdbc.sql("""
                INSERT INTO quality_measurement (id, brewery_id, plan_id, plan_version, point_id, parameter,
                    batch_id, instrument_id, instrument_fitness, value, unit, within_spec, note, measured_at,
                    measured_by)
                VALUES (:id, :brewery, :plan, :planVersion, :point, :parameter, :batch, :instrument,
                    :fitness, :value, :unit, :within, :note, :at, :by)
                """)
                .param("id", m.id()).param("brewery", m.breweryId()).param("plan", m.planId())
                .param("planVersion", m.planVersion()).param("point", m.pointId())
                .param("parameter", m.parameter()).param("batch", m.batchId())
                .param("instrument", m.instrumentId()).param("fitness", m.instrumentFitness())
                .param("value", m.value()).param("unit", m.unit()).param("within", m.withinSpec())
                .param("note", m.note()).param("at", Timestamp.from(m.measuredAt()))
                .param("by", m.measuredBy())
                .update();
    }

    @Override
    public void insertDeviation(Deviation d) {
        jdbc.sql("""
                INSERT INTO quality_deviation (id, brewery_id, measurement_id, plan_id, point_id, parameter,
                    severity, bound, limit_value, measured_value, unit, action, status, opened_at, opened_by)
                VALUES (:id, :brewery, :measurement, :plan, :point, :parameter, :severity, :bound, :limit,
                    :measured, :unit, :action, :status, :at, :by)
                """)
                .param("id", d.id()).param("brewery", d.breweryId()).param("measurement", d.measurementId())
                .param("plan", d.planId()).param("point", d.pointId()).param("parameter", d.parameter())
                .param("severity", d.severity().name()).param("bound", d.bound().name())
                .param("limit", d.limitValue()).param("measured", d.measuredValue()).param("unit", d.unit())
                .param("action", d.action()).param("status", d.status().name())
                .param("at", Timestamp.from(d.openedAt())).param("by", d.openedBy())
                .update();
    }

    @Override
    public List<Measurement> findByPlan(UUID breweryId, UUID planId) {
        return jdbc.sql("""
                SELECT id, brewery_id, plan_id, plan_version, point_id, parameter, batch_id, instrument_id,
                       instrument_fitness, value, unit, within_spec, note, measured_at, measured_by
                FROM quality_measurement
                WHERE brewery_id = :brewery AND plan_id = :plan
                ORDER BY measured_at DESC, id
                """)
                .param("brewery", breweryId).param("plan", planId)
                .query((rs, n) -> mapMeasurement(rs))
                .list();
    }

    @Override
    public List<Deviation> findOpenDeviations(UUID breweryId) {
        // Mais severos primeiro; dentro da mesma severidade, os mais recentes.
        return jdbc.sql("""
                SELECT id, brewery_id, measurement_id, plan_id, point_id, parameter, severity, bound,
                       limit_value, measured_value, unit, action, status, opened_at, opened_by
                FROM quality_deviation
                WHERE brewery_id = :brewery AND status = 'OPEN'
                ORDER BY CASE severity WHEN 'CRITICAL' THEN 0 WHEN 'MAJOR' THEN 1 ELSE 2 END,
                         opened_at DESC
                """)
                .param("brewery", breweryId)
                .query((rs, n) -> mapDeviation(rs))
                .list();
    }

    @Override
    public Optional<Deviation> findDeviation(UUID breweryId, UUID deviationId) {
        return jdbc.sql(DEVIATION_COLUMNS + " WHERE brewery_id = :brewery AND id = :id")
                .param("brewery", breweryId).param("id", deviationId)
                .query((rs, n) -> mapDeviation(rs))
                .optional();
    }

    /** Só o status muda: o desvio registra o que foi medido, e isso não se reescreve. */
    @Override
    public void updateDeviation(Deviation d) {
        jdbc.sql("UPDATE quality_deviation SET status = :status WHERE id = :id AND brewery_id = :brewery")
                .param("status", d.status().name()).param("id", d.id()).param("brewery", d.breweryId())
                .update();
    }

    private Measurement mapMeasurement(ResultSet rs) throws SQLException {
        return Measurement.reconstitute(
                rs.getObject("id", UUID.class),
                rs.getObject("brewery_id", UUID.class),
                rs.getObject("plan_id", UUID.class),
                rs.getInt("plan_version"),
                rs.getObject("point_id", UUID.class),
                rs.getString("parameter"),
                rs.getObject("batch_id", UUID.class),
                rs.getObject("instrument_id", UUID.class),
                rs.getString("instrument_fitness"),
                rs.getBigDecimal("value"),
                rs.getString("unit"),
                rs.getBoolean("within_spec"),
                rs.getString("note"),
                rs.getTimestamp("measured_at").toInstant(),
                rs.getObject("measured_by", UUID.class));
    }

    private Deviation mapDeviation(ResultSet rs) throws SQLException {
        return Deviation.reconstitute(
                rs.getObject("id", UUID.class),
                rs.getObject("brewery_id", UUID.class),
                rs.getObject("measurement_id", UUID.class),
                rs.getObject("plan_id", UUID.class),
                rs.getObject("point_id", UUID.class),
                rs.getString("parameter"),
                Severity.valueOf(rs.getString("severity")),
                SpecLimits.Bound.valueOf(rs.getString("bound")),
                rs.getBigDecimal("limit_value"),
                rs.getBigDecimal("measured_value"),
                rs.getString("unit"),
                rs.getString("action"),
                DeviationStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("opened_at").toInstant(),
                rs.getObject("opened_by", UUID.class));
    }
}
