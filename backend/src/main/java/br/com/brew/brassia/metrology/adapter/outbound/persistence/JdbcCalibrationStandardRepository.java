package br.com.brew.brassia.metrology.adapter.outbound.persistence;

import br.com.brew.brassia.metrology.application.port.outbound.CalibrationStandardRepository;
import br.com.brew.brassia.metrology.domain.CalibrationStandard;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcCalibrationStandardRepository implements CalibrationStandardRepository {

    private static final String COLUMNS = """
            SELECT id, brewery_id, code, description, certificate_number, issuer, traceability, valid_until,
                   version
            FROM metrology_standard
            """;

    private final JdbcClient jdbc;

    JdbcCalibrationStandardRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(CalibrationStandard s) {
        jdbc.sql("""
                INSERT INTO metrology_standard (id, brewery_id, code, description, certificate_number, issuer,
                    traceability, valid_until, version)
                VALUES (:id, :brewery, :code, :description, :certificate, :issuer, :traceability, :validUntil, 0)
                """)
                .param("id", s.id()).param("brewery", s.breweryId()).param("code", s.code())
                .param("description", s.description()).param("certificate", s.certificateNumber())
                .param("issuer", s.issuer()).param("traceability", s.traceability())
                .param("validUntil", s.validUntil())
                .update();
    }

    @Override
    public void update(CalibrationStandard s) {
        jdbc.sql("""
                UPDATE metrology_standard
                SET description = :description, certificate_number = :certificate, issuer = :issuer,
                    traceability = :traceability, valid_until = :validUntil, version = version + 1
                WHERE id = :id AND brewery_id = :brewery
                """)
                .param("description", s.description()).param("certificate", s.certificateNumber())
                .param("issuer", s.issuer()).param("traceability", s.traceability())
                .param("validUntil", s.validUntil())
                .param("id", s.id()).param("brewery", s.breweryId())
                .update();
    }

    @Override
    public Optional<CalibrationStandard> findById(UUID breweryId, UUID standardId) {
        return load(breweryId, standardId, "");
    }

    @Override
    public Optional<CalibrationStandard> lockById(UUID breweryId, UUID standardId) {
        return load(breweryId, standardId, " FOR UPDATE");
    }

    private Optional<CalibrationStandard> load(UUID breweryId, UUID standardId, String lock) {
        return jdbc.sql(COLUMNS + " WHERE brewery_id = :brewery AND id = :id" + lock)
                .param("brewery", breweryId).param("id", standardId)
                .query((rs, n) -> map(rs))
                .optional();
    }

    @Override
    public List<CalibrationStandard> findAll(UUID breweryId) {
        return jdbc.sql(COLUMNS + " WHERE brewery_id = :brewery ORDER BY code")
                .param("brewery", breweryId)
                .query((rs, n) -> map(rs))
                .list();
    }

    @Override
    public boolean existsByCode(UUID breweryId, String code) {
        return jdbc.sql("SELECT 1 FROM metrology_standard WHERE brewery_id = :brewery AND code = :code")
                .param("brewery", breweryId).param("code", code)
                .query(Integer.class).optional().isPresent();
    }

    private CalibrationStandard map(ResultSet rs) throws SQLException {
        return CalibrationStandard.reconstitute(
                rs.getObject("id", UUID.class),
                rs.getObject("brewery_id", UUID.class),
                rs.getString("code"),
                rs.getString("description"),
                rs.getString("certificate_number"),
                rs.getString("issuer"),
                rs.getString("traceability"),
                rs.getObject("valid_until", LocalDate.class),
                rs.getLong("version"));
    }
}
