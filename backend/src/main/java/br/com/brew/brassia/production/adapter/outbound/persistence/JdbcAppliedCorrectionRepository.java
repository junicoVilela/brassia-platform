package br.com.brew.brassia.production.adapter.outbound.persistence;

import br.com.brew.brassia.production.application.port.outbound.AppliedCorrectionRepository;
import br.com.brew.brassia.production.domain.AppliedCorrection;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAppliedCorrectionRepository implements AppliedCorrectionRepository {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, BigDecimal>> INPUTS = new TypeReference<>() {};

    private final JdbcClient jdbc;

    JdbcAppliedCorrectionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(AppliedCorrection c) {
        jdbc.sql("""
                INSERT INTO production_applied_correction (
                    id, brewery_id, batch_id, calculator, source_measurement_id, note, inputs, planned_value,
                    planned_unit, realized_value, applied_at, applied_by)
                VALUES (:id, :brewery, :batch, :calc, :source, :note, CAST(:inputs AS jsonb), :planned,
                        :plannedUnit, :realized, :at, :by)
                """)
                .param("id", c.id())
                .param("brewery", c.breweryId())
                .param("batch", c.batchId())
                .param("calc", c.calculator())
                .param("source", c.sourceMeasurementId())
                .param("note", c.note())
                .param("inputs", toJson(c.inputs()))
                .param("planned", c.plannedValue())
                .param("plannedUnit", c.plannedUnit())
                .param("realized", c.realizedValue())
                .param("at", Timestamp.from(c.appliedAt()))
                .param("by", c.appliedBy())
                .update();
    }

    @Override
    public List<AppliedCorrection> findByBatch(UUID breweryId, UUID batchId) {
        return jdbc.sql("""
                SELECT id, brewery_id, batch_id, calculator, source_measurement_id, note, inputs, planned_value,
                       planned_unit, realized_value, applied_at, applied_by
                FROM production_applied_correction
                WHERE brewery_id = :brewery AND batch_id = :batch
                ORDER BY applied_at
                """)
                .param("brewery", breweryId).param("batch", batchId)
                .query((rs, n) -> map(rs))
                .list();
    }

    private AppliedCorrection map(ResultSet rs) throws SQLException {
        return AppliedCorrection.reconstitute(
                rs.getObject("id", UUID.class),
                rs.getObject("brewery_id", UUID.class),
                rs.getObject("batch_id", UUID.class),
                rs.getString("calculator"),
                rs.getObject("source_measurement_id", UUID.class),
                rs.getString("note"),
                fromJson(rs.getString("inputs")),
                rs.getBigDecimal("planned_value"),
                rs.getString("planned_unit"),
                rs.getBigDecimal("realized_value"),
                rs.getTimestamp("applied_at").toInstant(),
                rs.getObject("applied_by", UUID.class));
    }

    private static String toJson(Map<String, BigDecimal> inputs) {
        try {
            return JSON.writeValueAsString(inputs);
        } catch (Exception e) {
            throw new IllegalStateException("falha ao serializar inputs da correção", e);
        }
    }

    private static Map<String, BigDecimal> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return JSON.readValue(json, INPUTS);
        } catch (Exception e) {
            throw new IllegalStateException("falha ao ler inputs da correção", e);
        }
    }
}
