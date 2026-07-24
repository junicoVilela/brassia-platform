package br.com.brew.brassia.referencedata.application.port.inbound;

import br.com.brew.brassia.referencedata.domain.ValidationIssue;
import java.util.List;
import java.util.UUID;

public interface ListImportJobsUseCase {

    List<JobView> handle(Query query);

    record Query(UUID breweryId, UUID sourceId) {}

    record JobView(UUID id, String datasetVersion, String contentType, long sizeBytes, String status,
            UUID publishedDatasetId, List<ValidationIssue> issues) {}
}
