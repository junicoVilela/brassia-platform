package br.com.brew.brassia.metrology.adapter.outbound.persistence;

import br.com.brew.brassia.metrology.application.port.outbound.InstrumentRepository;
import br.com.brew.brassia.metrology.domain.Calibration;
import br.com.brew.brassia.metrology.domain.CalibrationResult;
import br.com.brew.brassia.metrology.domain.Instrument;
import br.com.brew.brassia.metrology.domain.InstrumentState;
import br.com.brew.brassia.metrology.domain.InstrumentType;
import br.com.brew.brassia.metrology.domain.CorrectionCurve;
import br.com.brew.brassia.metrology.domain.CorrectionStep;
import br.com.brew.brassia.metrology.domain.CurvePoint;
import br.com.brew.brassia.metrology.domain.Fitness;
import br.com.brew.brassia.metrology.domain.MeasurementRange;
import br.com.brew.brassia.metrology.domain.ReadingCorrection;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * O instrumento é lido junto da sua última calibração (LEFT JOIN): é ela que decide a aptidão, e
 * buscá-la à parte abriria espaço para decidir sobre um estado desatualizado. O histórico completo
 * fica em {@link #findCalibrations}, e só é inserido — certificado não se reescreve.
 */
@Repository
class JdbcInstrumentRepository implements InstrumentRepository {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<CorrectionStep>> STEPS = new TypeReference<>() {};
    private static final TypeReference<List<String>> CAVEATS = new TypeReference<>() {};

    private static final String COLUMNS = """
            SELECT i.id, i.brewery_id, i.code, i.name, i.type, i.range_min, i.range_max, i.resolution,
                   i.accuracy, i.unit, i.location, i.state, i.block_reason, i.critical_use, i.version,
                   c.id AS cal_id, c.instrument_id AS cal_instrument, c.standard_id AS cal_standard,
                   c.standard_code AS cal_standard_code, c.performed_on AS cal_performed_on,
                   c.due_on AS cal_due_on, c.performed_by AS cal_performed_by,
                   c.certificate_number AS cal_certificate, c.result AS cal_result,
                   c.max_deviation AS cal_deviation, c.restriction AS cal_restriction, c.note AS cal_note
            FROM metrology_instrument i
            LEFT JOIN metrology_calibration c ON c.id = i.last_calibration_id
            """;

    private final JdbcClient jdbc;

    JdbcInstrumentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Instrument i) {
        jdbc.sql("""
                INSERT INTO metrology_instrument (id, brewery_id, code, name, type, range_min, range_max,
                    resolution, accuracy, unit, location, state, block_reason, critical_use,
                    last_calibration_id, version)
                VALUES (:id, :brewery, :code, :name, :type, :min, :max, :resolution, :accuracy, :unit,
                    :location, :state, :reason, :critical, NULL, 0)
                """)
                .param("id", i.id()).param("brewery", i.breweryId()).param("code", i.code())
                .param("name", i.name()).param("type", i.type().name())
                .param("min", i.range().min()).param("max", i.range().max())
                .param("resolution", i.range().resolution()).param("accuracy", i.range().accuracy())
                .param("unit", i.range().unit()).param("location", i.location())
                .param("state", i.state().name()).param("reason", i.blockReason())
                .param("critical", i.criticalUse())
                .update();
    }

    @Override
    public void update(Instrument i) {
        jdbc.sql("""
                UPDATE metrology_instrument
                SET name = :name, range_min = :min, range_max = :max, resolution = :resolution,
                    accuracy = :accuracy, unit = :unit, location = :location, state = :state,
                    block_reason = :reason, critical_use = :critical, last_calibration_id = :lastCalibration,
                    version = version + 1
                WHERE id = :id AND brewery_id = :brewery
                """)
                .param("name", i.name())
                .param("min", i.range().min()).param("max", i.range().max())
                .param("resolution", i.range().resolution()).param("accuracy", i.range().accuracy())
                .param("unit", i.range().unit()).param("location", i.location())
                .param("state", i.state().name()).param("reason", i.blockReason())
                .param("critical", i.criticalUse())
                .param("lastCalibration", i.lastCalibration().map(Calibration::id).orElse(null))
                .param("id", i.id()).param("brewery", i.breweryId())
                .update();
    }

    @Override
    public Optional<Instrument> findById(UUID breweryId, UUID instrumentId) {
        return load(breweryId, instrumentId, "");
    }

    @Override
    public Optional<Instrument> lockById(UUID breweryId, UUID instrumentId) {
        // FOR UPDATE OF i: o LEFT JOIN traz a calibração, que é histórico e não deve ser travada.
        return load(breweryId, instrumentId, " FOR UPDATE OF i");
    }

    private Optional<Instrument> load(UUID breweryId, UUID instrumentId, String lock) {
        return jdbc.sql(COLUMNS + " WHERE i.brewery_id = :brewery AND i.id = :id" + lock)
                .param("brewery", breweryId).param("id", instrumentId)
                .query((rs, n) -> map(rs))
                .optional();
    }

    @Override
    public List<Instrument> findAll(UUID breweryId) {
        return jdbc.sql(COLUMNS + " WHERE i.brewery_id = :brewery ORDER BY i.code")
                .param("brewery", breweryId)
                .query((rs, n) -> map(rs))
                .list();
    }

    @Override
    public boolean existsByCode(UUID breweryId, String code) {
        return jdbc.sql("SELECT 1 FROM metrology_instrument WHERE brewery_id = :brewery AND code = :code")
                .param("brewery", breweryId).param("code", code)
                .query(Integer.class).optional().isPresent();
    }

    @Override
    public void insertCalibration(Calibration c) {
        jdbc.sql("""
                INSERT INTO metrology_calibration (id, brewery_id, instrument_id, standard_id, standard_code,
                    performed_on, due_on, performed_by, certificate_number, result, max_deviation, restriction,
                    note)
                VALUES (:id, :brewery, :instrument, :standard, :standardCode, :performedOn, :dueOn,
                    :performedBy, :certificate, :result, :deviation, :restriction, :note)
                """)
                .param("id", c.id()).param("brewery", c.breweryId()).param("instrument", c.instrumentId())
                .param("standard", c.standardId()).param("standardCode", c.standardCode())
                .param("performedOn", c.performedOn()).param("dueOn", c.dueOn())
                .param("performedBy", c.performedBy()).param("certificate", c.certificateNumber())
                .param("result", c.result().name()).param("deviation", c.maxDeviation())
                .param("restriction", c.restriction()).param("note", c.note())
                .update();

        // A curva é parte do certificado: entra junto e nunca é reescrita depois.
        c.curve().ifPresent(curve -> curve.points().forEach(point -> jdbc.sql("""
                INSERT INTO metrology_calibration_point (id, brewery_id, calibration_id, reference, measured)
                VALUES (:id, :brewery, :calibration, :reference, :measured)
                """)
                .param("id", UUID.randomUUID()).param("brewery", c.breweryId())
                .param("calibration", c.id())
                .param("reference", point.reference()).param("measured", point.measured())
                .update()));
    }

    @Override
    public void insertCorrection(ReadingCorrection c) {
        jdbc.sql("""
                INSERT INTO metrology_reading_correction (id, brewery_id, instrument_id, source_reading_id,
                    raw_value, corrected_value, unit, sample_temp_c, calibration_temp_c, steps,
                    instrument_fitness, caveats, applied_at, applied_by)
                VALUES (:id, :brewery, :instrument, :source, :raw, :corrected, :unit, :sampleTemp,
                    :calibrationTemp, CAST(:steps AS jsonb), :fitness, CAST(:caveats AS jsonb), :at, :by)
                """)
                .param("id", c.id()).param("brewery", c.breweryId()).param("instrument", c.instrumentId())
                .param("source", c.sourceReadingId())
                .param("raw", c.rawValue()).param("corrected", c.correctedValue()).param("unit", c.unit())
                .param("sampleTemp", c.sampleTempC()).param("calibrationTemp", c.calibrationTempC())
                .param("steps", toJson(c.steps())).param("fitness", c.instrumentFitness().name())
                .param("caveats", toJson(c.caveats()))
                .param("at", Timestamp.from(c.appliedAt())).param("by", c.appliedBy())
                .update();
    }

    @Override
    public List<ReadingCorrection> findCorrections(UUID breweryId, UUID instrumentId) {
        return jdbc.sql("""
                SELECT id, brewery_id, instrument_id, source_reading_id, raw_value, corrected_value, unit,
                       sample_temp_c, calibration_temp_c, steps, instrument_fitness, caveats, applied_at,
                       applied_by
                FROM metrology_reading_correction
                WHERE brewery_id = :brewery AND instrument_id = :instrument
                ORDER BY applied_at DESC, id
                """)
                .param("brewery", breweryId).param("instrument", instrumentId)
                .query((rs, n) -> ReadingCorrection.reconstitute(
                        rs.getObject("id", UUID.class),
                        rs.getObject("brewery_id", UUID.class),
                        rs.getObject("instrument_id", UUID.class),
                        rs.getObject("source_reading_id", UUID.class),
                        rs.getBigDecimal("raw_value"),
                        rs.getBigDecimal("corrected_value"),
                        rs.getString("unit"),
                        rs.getBigDecimal("sample_temp_c"),
                        rs.getBigDecimal("calibration_temp_c"),
                        fromJson(rs.getString("steps"), STEPS),
                        Fitness.valueOf(rs.getString("instrument_fitness")),
                        fromJson(rs.getString("caveats"), CAVEATS),
                        rs.getTimestamp("applied_at").toInstant(),
                        rs.getObject("applied_by", UUID.class)))
                .list();
    }

    private static String toJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("falha ao serializar dados da correção", e);
        }
    }

    private static <T> T fromJson(String json, TypeReference<T> type) {
        try {
            return JSON.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("falha ao ler dados da correção", e);
        }
    }

    /** Pontos conferidos pelo certificado; ausentes quando ele só declara o desvio máximo. */
    private CorrectionCurve loadCurve(UUID calibrationId) {
        var points = jdbc.sql("""
                SELECT reference, measured FROM metrology_calibration_point
                WHERE calibration_id = :calibration ORDER BY measured
                """)
                .param("calibration", calibrationId)
                .query((rs, n) -> new CurvePoint(rs.getBigDecimal("reference"), rs.getBigDecimal("measured")))
                .list();
        return points.isEmpty() ? null : CorrectionCurve.of(points);
    }

    @Override
    public List<Calibration> findCalibrations(UUID breweryId, UUID instrumentId) {
        return jdbc.sql("""
                SELECT id AS cal_id, brewery_id, instrument_id AS cal_instrument, standard_id AS cal_standard,
                       standard_code AS cal_standard_code, performed_on AS cal_performed_on,
                       due_on AS cal_due_on, performed_by AS cal_performed_by,
                       certificate_number AS cal_certificate, result AS cal_result,
                       max_deviation AS cal_deviation, restriction AS cal_restriction, note AS cal_note
                FROM metrology_calibration
                WHERE brewery_id = :brewery AND instrument_id = :instrument
                ORDER BY performed_on DESC, id
                """)
                .param("brewery", breweryId).param("instrument", instrumentId)
                .query((rs, n) -> mapCalibration(rs, rs.getObject("brewery_id", UUID.class)))
                .list();
    }

    private Instrument map(ResultSet rs) throws SQLException {
        var breweryId = rs.getObject("brewery_id", UUID.class);
        var range = new MeasurementRange(rs.getBigDecimal("range_min"), rs.getBigDecimal("range_max"),
                rs.getBigDecimal("resolution"), rs.getBigDecimal("accuracy"), rs.getString("unit"));
        return Instrument.reconstitute(
                rs.getObject("id", UUID.class),
                breweryId,
                rs.getString("code"),
                rs.getString("name"),
                InstrumentType.valueOf(rs.getString("type")),
                range,
                rs.getString("location"),
                InstrumentState.valueOf(rs.getString("state")),
                rs.getString("block_reason"),
                rs.getBoolean("critical_use"),
                rs.getObject("cal_id", UUID.class) == null ? null : mapCalibration(rs, breweryId),
                rs.getLong("version"));
    }

    private Calibration mapCalibration(ResultSet rs, UUID breweryId) throws SQLException {
        return Calibration.reconstitute(
                rs.getObject("cal_id", UUID.class),
                breweryId,
                rs.getObject("cal_instrument", UUID.class),
                rs.getObject("cal_standard", UUID.class),
                rs.getString("cal_standard_code"),
                rs.getObject("cal_performed_on", LocalDate.class),
                rs.getObject("cal_due_on", LocalDate.class),
                rs.getString("cal_performed_by"),
                rs.getString("cal_certificate"),
                CalibrationResult.valueOf(rs.getString("cal_result")),
                rs.getBigDecimal("cal_deviation"),
                rs.getString("cal_restriction"),
                rs.getString("cal_note"),
                loadCurve(rs.getObject("cal_id", UUID.class)));
    }
}
