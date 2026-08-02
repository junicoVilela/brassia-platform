package br.com.brew.brassia.gas.adapter.outbound.persistence;

import br.com.brew.brassia.gas.application.port.outbound.ServiceLineRepository;
import br.com.brew.brassia.gas.domain.LineResistance;
import br.com.brew.brassia.gas.domain.ServiceLine;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcServiceLineRepository implements ServiceLineRepository {

    private static final String LINE_COLUMNS = """
            SELECT id, brewery_id, code, name, point_of_use_equipment_id, current_revision, version
            FROM gas_service_line
            """;

    private static final String TUBING_COLUMNS = """
            SELECT id, brewery_id, material, internal_diameter_mm, resistance_bar_per_meter,
                   reference_flow_lpm, version
            FROM gas_line_resistance
            """;

    private final JdbcClient jdbc;

    JdbcServiceLineRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(ServiceLine line) {
        jdbc.sql("""
                INSERT INTO gas_service_line (id, brewery_id, code, name, point_of_use_equipment_id,
                    current_revision, version)
                VALUES (:id, :brewery, :code, :name, :point, :revision, 0)
                """)
                .param("id", line.id())
                .param("brewery", line.breweryId())
                .param("code", line.code())
                .param("name", line.name())
                .param("point", line.pointOfUseEquipmentId())
                .param("revision", line.currentRevision())
                .update();
    }

    @Override
    public Optional<ServiceLine> findById(UUID breweryId, UUID lineId) {
        return loadLine(breweryId, lineId, "");
    }

    @Override
    public Optional<ServiceLine> findForUpdate(UUID breweryId, UUID lineId) {
        return loadLine(breweryId, lineId, " FOR UPDATE");
    }

    private Optional<ServiceLine> loadLine(UUID breweryId, UUID lineId, String lock) {
        return jdbc.sql(LINE_COLUMNS + " WHERE brewery_id = :brewery AND id = :id" + lock)
                .param("brewery", breweryId).param("id", lineId)
                .query((rs, n) -> mapLine(rs))
                .optional();
    }

    @Override
    public List<ServiceLine> findAll(UUID breweryId) {
        return jdbc.sql(LINE_COLUMNS + " WHERE brewery_id = :brewery ORDER BY code")
                .param("brewery", breweryId)
                .query((rs, n) -> mapLine(rs))
                .list();
    }

    @Override
    public boolean existsByCode(UUID breweryId, String code) {
        return jdbc.sql("SELECT 1 FROM gas_service_line WHERE brewery_id = :brewery AND code = :code")
                .param("brewery", breweryId).param("code", code)
                .query(Integer.class)
                .optional()
                .isPresent();
    }

    @Override
    public boolean update(ServiceLine line, long expectedVersion) {
        return jdbc.sql("""
                UPDATE gas_service_line
                SET name = :name, current_revision = :revision, version = version + 1
                WHERE id = :id AND brewery_id = :brewery AND version = :version
                """)
                .param("name", line.name())
                .param("revision", line.currentRevision())
                .param("id", line.id())
                .param("brewery", line.breweryId())
                .param("version", expectedVersion)
                .update() == 1;
    }

    @Override
    public void insertRevision(ServiceLine.Revision r) {
        jdbc.sql("""
                INSERT INTO gas_service_line_revision (id, line_id, brewery_id, revision, material,
                    internal_diameter_mm, applied_length_meters, recommended_length_meters,
                    applied_pressure_bar, elevation_meters, residual_pressure_bar, target_flow_lpm,
                    serving_temp_c, target_co2_volumes, calculation_method, calculator_version, note,
                    applied_by, applied_at)
                VALUES (:id, :line, :brewery, :revision, :material, :diameter, :applied, :recommended,
                    :pressure, :elevation, :residual, :flow, :temp, :volumes, :method, :calcVersion, :note,
                    :by, :at)
                """)
                .param("id", r.id())
                .param("line", r.lineId())
                .param("brewery", r.breweryId())
                .param("revision", r.revision())
                .param("material", r.material())
                .param("diameter", r.internalDiameterMm())
                .param("applied", r.appliedLengthMeters())
                .param("recommended", r.recommendedLengthMeters())
                .param("pressure", r.appliedPressureBar())
                .param("elevation", r.elevationMeters())
                .param("residual", r.residualPressureBar())
                .param("flow", r.targetFlowLpm())
                .param("temp", r.servingTempC())
                .param("volumes", r.targetCo2Volumes())
                .param("method", r.calculationMethod())
                .param("calcVersion", r.calculatorVersion())
                .param("note", r.note())
                .param("by", r.appliedBy())
                .param("at", Timestamp.from(r.appliedAt()))
                .update();
    }

    @Override
    public List<ServiceLine.Revision> findRevisions(UUID breweryId, UUID lineId) {
        return jdbc.sql("""
                SELECT id, line_id, brewery_id, revision, material, internal_diameter_mm,
                       applied_length_meters, recommended_length_meters, applied_pressure_bar,
                       elevation_meters, residual_pressure_bar, target_flow_lpm, serving_temp_c,
                       target_co2_volumes, calculation_method, calculator_version, note, applied_by, applied_at
                FROM gas_service_line_revision
                WHERE brewery_id = :brewery AND line_id = :line
                ORDER BY revision DESC
                """)
                .param("brewery", breweryId).param("line", lineId)
                .query((rs, n) -> new ServiceLine.Revision(
                        rs.getObject("id", UUID.class),
                        rs.getObject("line_id", UUID.class),
                        rs.getObject("brewery_id", UUID.class),
                        rs.getInt("revision"),
                        rs.getString("material"),
                        rs.getBigDecimal("internal_diameter_mm"),
                        rs.getBigDecimal("applied_length_meters"),
                        rs.getBigDecimal("recommended_length_meters"),
                        rs.getBigDecimal("applied_pressure_bar"),
                        rs.getBigDecimal("elevation_meters"),
                        rs.getBigDecimal("residual_pressure_bar"),
                        rs.getBigDecimal("target_flow_lpm"),
                        rs.getBigDecimal("serving_temp_c"),
                        rs.getBigDecimal("target_co2_volumes"),
                        rs.getString("calculation_method"),
                        rs.getString("calculator_version"),
                        rs.getString("note"),
                        rs.getObject("applied_by", UUID.class),
                        rs.getTimestamp("applied_at").toInstant()))
                .list();
    }

    // --- catálogo de tubos ---

    @Override
    public void insertResistance(LineResistance r) {
        jdbc.sql("""
                INSERT INTO gas_line_resistance (id, brewery_id, material, internal_diameter_mm,
                    resistance_bar_per_meter, reference_flow_lpm, version)
                VALUES (:id, :brewery, :material, :diameter, :resistance, :flow, 0)
                """)
                .param("id", r.id())
                .param("brewery", r.breweryId())
                .param("material", r.material())
                .param("diameter", r.internalDiameterMm())
                .param("resistance", r.resistanceBarPerMeter())
                .param("flow", r.referenceFlowLpm())
                .update();
    }

    @Override
    public Optional<LineResistance> findResistance(UUID breweryId, UUID resistanceId) {
        return jdbc.sql(TUBING_COLUMNS + " WHERE brewery_id = :brewery AND id = :id")
                .param("brewery", breweryId).param("id", resistanceId)
                .query((rs, n) -> mapTubing(rs))
                .optional();
    }

    @Override
    public Optional<LineResistance> findResistanceBySpec(UUID breweryId, String material,
            BigDecimal internalDiameterMm) {
        return jdbc.sql(TUBING_COLUMNS
                        + " WHERE brewery_id = :brewery AND material = :material"
                        + " AND internal_diameter_mm = :diameter")
                .param("brewery", breweryId).param("material", material).param("diameter", internalDiameterMm)
                .query((rs, n) -> mapTubing(rs))
                .optional();
    }

    @Override
    public List<LineResistance> findAllResistances(UUID breweryId) {
        return jdbc.sql(TUBING_COLUMNS + " WHERE brewery_id = :brewery ORDER BY material, internal_diameter_mm")
                .param("brewery", breweryId)
                .query((rs, n) -> mapTubing(rs))
                .list();
    }

    @Override
    public boolean updateResistance(LineResistance r, long expectedVersion) {
        return jdbc.sql("""
                UPDATE gas_line_resistance
                SET resistance_bar_per_meter = :resistance, reference_flow_lpm = :flow, version = version + 1
                WHERE id = :id AND brewery_id = :brewery AND version = :version
                """)
                .param("resistance", r.resistanceBarPerMeter())
                .param("flow", r.referenceFlowLpm())
                .param("id", r.id())
                .param("brewery", r.breweryId())
                .param("version", expectedVersion)
                .update() == 1;
    }

    private ServiceLine mapLine(ResultSet rs) throws SQLException {
        return ServiceLine.reconstitute(
                rs.getObject("id", UUID.class),
                rs.getObject("brewery_id", UUID.class),
                rs.getString("code"),
                rs.getString("name"),
                rs.getObject("point_of_use_equipment_id", UUID.class),
                rs.getInt("current_revision"),
                rs.getLong("version"));
    }

    private LineResistance mapTubing(ResultSet rs) throws SQLException {
        return LineResistance.reconstitute(
                rs.getObject("id", UUID.class),
                rs.getObject("brewery_id", UUID.class),
                rs.getString("material"),
                rs.getBigDecimal("internal_diameter_mm"),
                rs.getBigDecimal("resistance_bar_per_meter"),
                rs.getBigDecimal("reference_flow_lpm"),
                rs.getLong("version"));
    }
}
