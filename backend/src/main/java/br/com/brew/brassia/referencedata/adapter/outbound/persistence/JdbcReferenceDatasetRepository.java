package br.com.brew.brassia.referencedata.adapter.outbound.persistence;

import br.com.brew.brassia.referencedata.application.port.outbound.ReferenceDatasetRepository;
import br.com.brew.brassia.referencedata.domain.Checksum;
import br.com.brew.brassia.referencedata.domain.DatasetStatus;
import br.com.brew.brassia.referencedata.domain.DatasetVersion;
import br.com.brew.brassia.referencedata.domain.Provenance;
import br.com.brew.brassia.referencedata.domain.ReferenceDataset;
import br.com.brew.brassia.referencedata.domain.ReferenceDatasetId;
import br.com.brew.brassia.referencedata.domain.ReferenceSourceId;
import br.com.brew.brassia.referencedata.domain.ReviewStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcReferenceDatasetRepository implements ReferenceDatasetRepository {

    private static final String COLUMNS =
            "id, source_id, dataset_version, checksum, source_system, source_record_id, source_url, retrieved_at, "
                    + "raw_payload, effective_from, effective_to, status, review_status, published_at, version";

    private final JdbcClient jdbc;

    JdbcReferenceDatasetRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ReferenceDataset> findByChecksum(UUID sourceId, String checksum) {
        return jdbc.sql("SELECT " + COLUMNS + """
                 FROM reference_dataset WHERE source_id = :source AND checksum = :checksum
                """)
                .param("source", sourceId).param("checksum", checksum)
                .query((rs, n) -> map(rs)).optional();
    }

    @Override
    public void insert(ReferenceDataset d) {
        jdbc.sql("""
                INSERT INTO reference_dataset
                    (id, source_id, dataset_version, checksum, source_system, source_record_id, source_url,
                     retrieved_at, raw_payload, effective_from, effective_to, status, review_status, published_at,
                     version)
                VALUES (:id, :source, :version, :checksum, :system, :recordId, :sourceUrl,
                        :retrievedAt, :payload, :effectiveFrom, :effectiveTo, :status, :review, :publishedAt,
                        :optimistic)
                """)
                .param("id", d.id().value())
                .param("source", d.sourceId().value())
                .param("version", d.version().value())
                .param("checksum", d.checksum().value())
                .param("system", d.provenance().sourceSystem())
                .param("recordId", d.provenance().sourceRecordId())
                .param("sourceUrl", d.provenance().sourceUrl())
                .param("retrievedAt", Timestamp.from(d.provenance().retrievedAt()))
                .param("payload", d.rawPayload())
                .param("effectiveFrom", Timestamp.from(d.effectiveFrom()))
                .param("effectiveTo", d.effectiveTo() == null ? null : Timestamp.from(d.effectiveTo()))
                .param("status", d.status().name())
                .param("review", d.reviewStatus().name())
                .param("publishedAt", d.publishedAt() == null ? null : Timestamp.from(d.publishedAt()))
                .param("optimistic", d.optimisticVersion())
                .update();
    }

    @Override
    public Optional<ReferenceDataset> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM reference_dataset WHERE id = :id")
                .param("id", id)
                .query((rs, n) -> map(rs)).optional();
    }

    @Override
    public List<ReferenceDataset> findBySource(UUID sourceId) {
        return jdbc.sql("SELECT " + COLUMNS + """
                 FROM reference_dataset WHERE source_id = :source ORDER BY effective_from DESC
                """)
                .param("source", sourceId)
                .query((rs, n) -> map(rs)).list();
    }

    @Override
    public boolean markPublished(UUID id, Instant publishedAt, long expectedVersion) {
        int updated = jdbc.sql("""
                UPDATE reference_dataset
                SET status = 'PUBLISHED', review_status = 'APPROVED', published_at = :at,
                    version = :newVersion, updated_at = now()
                WHERE id = :id AND version = :expected AND status = 'DRAFT'
                """)
                .param("at", Timestamp.from(publishedAt))
                .param("newVersion", expectedVersion + 1)
                .param("id", id)
                .param("expected", expectedVersion)
                .update();
        return updated > 0;
    }

    private static ReferenceDataset map(ResultSet rs) throws SQLException {
        var provenance = new Provenance(
                rs.getString("source_system"),
                rs.getString("source_record_id"),
                rs.getString("source_url"),
                instant(rs, "retrieved_at"));
        return ReferenceDataset.reconstitute(
                new ReferenceDatasetId(rs.getObject("id", UUID.class)),
                new ReferenceSourceId(rs.getObject("source_id", UUID.class)),
                new DatasetVersion(rs.getString("dataset_version")),
                new Checksum(rs.getString("checksum")),
                provenance,
                rs.getString("raw_payload"),
                instant(rs, "effective_from"),
                instant(rs, "effective_to"),
                DatasetStatus.valueOf(rs.getString("status")),
                ReviewStatus.valueOf(rs.getString("review_status")),
                instant(rs, "published_at"),
                rs.getLong("version"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
