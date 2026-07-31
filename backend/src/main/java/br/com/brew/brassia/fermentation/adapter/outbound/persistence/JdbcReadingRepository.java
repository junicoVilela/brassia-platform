package br.com.brew.brassia.fermentation.adapter.outbound.persistence;

import br.com.brew.brassia.fermentation.application.port.outbound.ReadingRepository;
import br.com.brew.brassia.fermentation.domain.FermentationReading;
import br.com.brew.brassia.fermentation.domain.ReadingKind;
import br.com.brew.brassia.fermentation.domain.ReadingSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcReadingRepository implements ReadingRepository {

    private static final String COLUMNS = """
            SELECT id, brewery_id, batch_id, kind, source, value, unit, measured_at, valid, invalid_reason
            FROM fermentation_reading
            """;

    private final JdbcClient jdbc;

    JdbcReadingRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UpsertResult upsertIfAbsent(FermentationReading r) {
        var insertedId = jdbc.sql("""
                INSERT INTO fermentation_reading (id, brewery_id, batch_id, kind, source, value, unit, measured_at,
                    valid, invalid_reason)
                VALUES (:id, :brewery, :batch, :kind, :source, :value, :unit, :at, :valid, :reason)
                ON CONFLICT (batch_id, kind, source, measured_at) DO NOTHING
                RETURNING id
                """)
                .param("id", r.id())
                .param("brewery", r.breweryId())
                .param("batch", r.batchId())
                .param("kind", r.kind().name())
                .param("source", r.source().name())
                .param("value", r.value())
                .param("unit", r.unit())
                .param("at", Timestamp.from(r.measuredAt()))
                .param("valid", r.valid())
                .param("reason", r.invalidReason())
                .query(UUID.class)
                .optional();

        if (insertedId.isPresent()) {
            return new UpsertResult(r, true);
        }
        var existing = jdbc.sql(COLUMNS + """
                 WHERE batch_id = :batch AND kind = :kind AND source = :source AND measured_at = :at
                """)
                .param("batch", r.batchId()).param("kind", r.kind().name())
                .param("source", r.source().name()).param("at", Timestamp.from(r.measuredAt()))
                .query((rs, n) -> map(rs))
                .single();
        return new UpsertResult(existing, false);
    }

    @Override
    public List<FermentationReading> findSeries(UUID breweryId, UUID batchId, ReadingKind kind) {
        var sql = COLUMNS + " WHERE brewery_id = :brewery AND batch_id = :batch"
                + (kind == null ? "" : " AND kind = :kind") + " ORDER BY measured_at, kind";
        var spec = jdbc.sql(sql).param("brewery", breweryId).param("batch", batchId);
        if (kind != null) {
            spec = spec.param("kind", kind.name());
        }
        return spec.query((rs, n) -> map(rs)).list();
    }

    private FermentationReading map(ResultSet rs) throws SQLException {
        return FermentationReading.reconstitute(
                rs.getObject("id", UUID.class),
                rs.getObject("brewery_id", UUID.class),
                rs.getObject("batch_id", UUID.class),
                ReadingKind.valueOf(rs.getString("kind")),
                ReadingSource.valueOf(rs.getString("source")),
                rs.getBigDecimal("value"),
                rs.getString("unit"),
                rs.getTimestamp("measured_at").toInstant(),
                rs.getBoolean("valid"),
                rs.getString("invalid_reason"));
    }
}
