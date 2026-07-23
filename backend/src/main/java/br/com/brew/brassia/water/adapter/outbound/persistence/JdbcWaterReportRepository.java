package br.com.brew.brassia.water.adapter.outbound.persistence;

import br.com.brew.brassia.water.application.port.outbound.WaterReportRepository;
import br.com.brew.brassia.water.domain.IonProfile;
import br.com.brew.brassia.water.domain.WaterMethod;
import br.com.brew.brassia.water.domain.WaterReport;
import br.com.brew.brassia.water.domain.WaterReportId;
import br.com.brew.brassia.water.domain.WaterSourceId;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcWaterReportRepository implements WaterReportRepository {
    private final JdbcClient jdbc;

    JdbcWaterReportRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(WaterReport r) {
        var ions = r.ions();
        jdbc.sql("""
                INSERT INTO water_report (
                    id, brewery_id, source_id, collected_on, method, calcium, magnesium, sodium, sulfate,
                    chloride, bicarbonate, notes, created_at)
                VALUES (:id, :brewery, :source, :collectedOn, :method, :calcium, :magnesium, :sodium, :sulfate,
                        :chloride, :bicarbonate, :notes, :at)
                """)
                .param("id", r.id().value())
                .param("brewery", r.breweryId())
                .param("source", r.sourceId().value())
                .param("collectedOn", Date.valueOf(r.collectedOn()))
                .param("method", r.method().name())
                .param("calcium", ions.calcium())
                .param("magnesium", ions.magnesium())
                .param("sodium", ions.sodium())
                .param("sulfate", ions.sulfate())
                .param("chloride", ions.chloride())
                .param("bicarbonate", ions.bicarbonate())
                .param("notes", r.notes())
                .param("at", Timestamp.from(Instant.now()))
                .update();
    }

    @Override
    public List<WaterReport> findBySource(UUID breweryId, UUID sourceId) {
        return jdbc.sql("""
                SELECT id, brewery_id, source_id, collected_on, method, calcium, magnesium, sodium, sulfate,
                       chloride, bicarbonate, notes
                FROM water_report
                WHERE brewery_id = :brewery AND source_id = :source
                ORDER BY collected_on DESC, created_at DESC
                """)
                .param("brewery", breweryId).param("source", sourceId)
                .query((rs, n) -> map(rs)).list();
    }

    private static WaterReport map(ResultSet rs) throws SQLException {
        var ions = new IonProfile(
                rs.getBigDecimal("calcium"), rs.getBigDecimal("magnesium"), rs.getBigDecimal("sodium"),
                rs.getBigDecimal("sulfate"), rs.getBigDecimal("chloride"), rs.getBigDecimal("bicarbonate"));
        return WaterReport.reconstitute(
                new WaterReportId(rs.getObject("id", UUID.class)),
                rs.getObject("brewery_id", UUID.class),
                new WaterSourceId(rs.getObject("source_id", UUID.class)),
                rs.getObject("collected_on", LocalDate.class),
                WaterMethod.valueOf(rs.getString("method")),
                ions,
                rs.getString("notes"));
    }
}
