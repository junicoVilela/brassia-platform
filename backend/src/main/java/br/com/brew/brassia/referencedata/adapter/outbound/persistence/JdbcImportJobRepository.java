package br.com.brew.brassia.referencedata.adapter.outbound.persistence;

import br.com.brew.brassia.referencedata.application.port.outbound.ImportJobRepository;
import br.com.brew.brassia.referencedata.domain.Checksum;
import br.com.brew.brassia.referencedata.domain.ImportJob;
import br.com.brew.brassia.referencedata.domain.ImportJobId;
import br.com.brew.brassia.referencedata.domain.ImportJobStatus;
import br.com.brew.brassia.referencedata.domain.ReferenceDatasetId;
import br.com.brew.brassia.referencedata.domain.ReferenceSourceId;
import br.com.brew.brassia.referencedata.domain.ValidationIssue;
import br.com.brew.brassia.referencedata.domain.ValidationSeverity;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcImportJobRepository implements ImportJobRepository {

    private static final String COLUMNS =
            "id, source_id, brewery_id, dataset_version, content_type, size_bytes, checksum, raw_payload, status, "
                    + "published_dataset_id, version";

    private final JdbcClient jdbc;

    JdbcImportJobRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(ImportJob job) {
        jdbc.sql("""
                INSERT INTO import_job
                    (id, source_id, brewery_id, dataset_version, content_type, size_bytes, checksum, raw_payload,
                     status, published_dataset_id, version)
                VALUES (:id, :source, :brewery, :version, :contentType, :size, :checksum, :payload, :status,
                        :dataset, :optimistic)
                """)
                .param("id", job.id().value())
                .param("source", job.sourceId().value())
                .param("brewery", job.breweryId())
                .param("version", job.datasetVersion())
                .param("contentType", job.contentType())
                .param("size", job.sizeBytes())
                .param("checksum", job.checksum().value())
                .param("payload", job.rawPayload())
                .param("status", job.status().name())
                .param("dataset", job.publishedDatasetId() == null ? null : job.publishedDatasetId().value())
                .param("optimistic", job.optimisticVersion())
                .update();
        insertIssues(job);
    }

    private void insertIssues(ImportJob job) {
        for (ValidationIssue issue : job.issues()) {
            jdbc.sql("""
                    INSERT INTO import_job_issue (id, job_id, line, field, code, message, severity)
                    VALUES (:id, :job, :line, :field, :code, :message, :severity)
                    """)
                    .param("id", UUID.randomUUID())
                    .param("job", job.id().value())
                    .param("line", issue.line())
                    .param("field", issue.field())
                    .param("code", issue.code())
                    .param("message", issue.message())
                    .param("severity", issue.severity().name())
                    .update();
        }
    }

    @Override
    public Optional<ImportJob> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM import_job WHERE id = :id")
                .param("id", id)
                .query((rs, n) -> map(rs)).optional();
    }

    @Override
    public List<ImportJob> findBySource(UUID sourceId) {
        return jdbc.sql("SELECT " + COLUMNS + """
                 FROM import_job WHERE source_id = :source ORDER BY created_at DESC
                """)
                .param("source", sourceId)
                .query((rs, n) -> map(rs)).list();
    }

    @Override
    public boolean markPublished(UUID id, UUID publishedDatasetId, long expectedVersion) {
        int updated = jdbc.sql("""
                UPDATE import_job
                SET status = 'PUBLISHED', published_dataset_id = :dataset, version = :newVersion, updated_at = now()
                WHERE id = :id AND version = :expected AND status = 'REVIEW_REQUIRED'
                """)
                .param("dataset", publishedDatasetId)
                .param("newVersion", expectedVersion + 1)
                .param("id", id)
                .param("expected", expectedVersion)
                .update();
        return updated > 0;
    }

    private ImportJob map(ResultSet rs) throws SQLException {
        var jobId = rs.getObject("id", UUID.class);
        UUID datasetId = rs.getObject("published_dataset_id", UUID.class);
        return ImportJob.reconstitute(
                new ImportJobId(jobId),
                new ReferenceSourceId(rs.getObject("source_id", UUID.class)),
                rs.getObject("brewery_id", UUID.class),
                rs.getString("dataset_version"),
                rs.getString("content_type"),
                rs.getLong("size_bytes"),
                new Checksum(rs.getString("checksum")),
                rs.getString("raw_payload"),
                ImportJobStatus.valueOf(rs.getString("status")),
                loadIssues(jobId),
                datasetId == null ? null : new ReferenceDatasetId(datasetId),
                rs.getLong("version"));
    }

    private List<ValidationIssue> loadIssues(UUID jobId) {
        return jdbc.sql("""
                SELECT line, field, code, message, severity FROM import_job_issue
                WHERE job_id = :job ORDER BY created_at
                """)
                .param("job", jobId)
                .query((rs, n) -> new ValidationIssue(
                        (Integer) rs.getObject("line"),
                        rs.getString("field"),
                        rs.getString("code"),
                        rs.getString("message"),
                        ValidationSeverity.valueOf(rs.getString("severity"))))
                .list();
    }
}
