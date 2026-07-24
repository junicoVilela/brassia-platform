package br.com.brew.brassia.referencedata.adapter.inbound.web.dto;

import br.com.brew.brassia.referencedata.application.port.inbound.ListImportJobsUseCase;
import br.com.brew.brassia.referencedata.application.port.inbound.PublishImportJobUseCase;
import br.com.brew.brassia.referencedata.application.port.inbound.SubmitImportJobUseCase;
import java.util.List;
import java.util.UUID;

public record ImportJobResponse(
        UUID id,
        String datasetVersion,
        String contentType,
        Long sizeBytes,
        String status,
        UUID publishedDatasetId,
        List<ValidationIssueResponse> issues) {

    public static ImportJobResponse from(SubmitImportJobUseCase.Result r) {
        return new ImportJobResponse(r.jobId(), null, null, null, r.status(), null,
                ValidationIssueResponse.fromAll(r.issues()));
    }

    public static ImportJobResponse from(ListImportJobsUseCase.JobView v) {
        return new ImportJobResponse(v.id(), v.datasetVersion(), v.contentType(), v.sizeBytes(), v.status(),
                v.publishedDatasetId(), ValidationIssueResponse.fromAll(v.issues()));
    }

    public static ImportJobResponse from(PublishImportJobUseCase.Result r) {
        return new ImportJobResponse(r.jobId(), null, null, null, r.status(), r.datasetId(), List.of());
    }
}
