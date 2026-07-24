package br.com.brew.brassia.referencedata.adapter.inbound.web;

import br.com.brew.brassia.referencedata.adapter.inbound.web.dto.ImportJobResponse;
import br.com.brew.brassia.referencedata.adapter.inbound.web.dto.ReferenceDatasetResponse;
import br.com.brew.brassia.referencedata.adapter.inbound.web.dto.ReferenceIdResponse;
import br.com.brew.brassia.referencedata.adapter.inbound.web.dto.ReferenceSourceResponse;
import br.com.brew.brassia.referencedata.adapter.inbound.web.dto.RecordReferenceDatasetRequest;
import br.com.brew.brassia.referencedata.adapter.inbound.web.dto.RegisterReferenceSourceRequest;
import br.com.brew.brassia.referencedata.adapter.inbound.web.dto.SubmitImportJobRequest;
import br.com.brew.brassia.referencedata.application.port.inbound.ListImportJobsUseCase;
import br.com.brew.brassia.referencedata.application.port.inbound.ListReferenceDatasetsUseCase;
import br.com.brew.brassia.referencedata.application.port.inbound.ListReferenceSourcesUseCase;
import br.com.brew.brassia.referencedata.application.port.inbound.PublishImportJobUseCase;
import br.com.brew.brassia.referencedata.application.port.inbound.PublishReferenceDatasetUseCase;
import br.com.brew.brassia.referencedata.application.port.inbound.RecordReferenceDatasetUseCase;
import br.com.brew.brassia.referencedata.application.port.inbound.RegisterReferenceSourceUseCase;
import br.com.brew.brassia.referencedata.application.port.inbound.SubmitImportJobUseCase;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import br.com.brew.brassia.shared.web.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reference")
final class ReferenceDataController {

    private final RegisterReferenceSourceUseCase registerSource;
    private final ListReferenceSourcesUseCase listSources;
    private final RecordReferenceDatasetUseCase recordDataset;
    private final ListReferenceDatasetsUseCase listDatasets;
    private final PublishReferenceDatasetUseCase publishDataset;
    private final SubmitImportJobUseCase submitJob;
    private final ListImportJobsUseCase listJobs;
    private final PublishImportJobUseCase publishJob;

    ReferenceDataController(RegisterReferenceSourceUseCase registerSource, ListReferenceSourcesUseCase listSources,
            RecordReferenceDatasetUseCase recordDataset, ListReferenceDatasetsUseCase listDatasets,
            PublishReferenceDatasetUseCase publishDataset, SubmitImportJobUseCase submitJob,
            ListImportJobsUseCase listJobs, PublishImportJobUseCase publishJob) {
        this.registerSource = registerSource;
        this.listSources = listSources;
        this.recordDataset = recordDataset;
        this.listDatasets = listDatasets;
        this.publishDataset = publishDataset;
        this.submitJob = submitJob;
        this.listJobs = listJobs;
        this.publishJob = publishJob;
    }

    @GetMapping("/sources")
    PageResponse<ReferenceSourceResponse> sources(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("reference.read");
        var result = listSources.handle(new ListReferenceSourcesUseCase.Query(principal.requireBrewery(), page, size));
        var content = result.content().stream().map(ReferenceSourceResponse::from).toList();
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) result.total() / size);
        return new PageResponse<>(content, page, size, result.total(), totalPages);
    }

    @PostMapping("/sources")
    ResponseEntity<ReferenceIdResponse> registerSource(
            @Valid @RequestBody RegisterReferenceSourceRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("reference.manage");
        var result = registerSource.handle(new RegisterReferenceSourceUseCase.Command(
                principal.userId(), principal.requireBrewery(), request.type(), request.name(), request.owner(),
                request.url(), request.licenseName(), request.permissionStatus(), request.attribution(),
                request.reviewFrequency(), request.responsible()));
        return ResponseEntity.created(URI.create("/api/v1/reference/sources/" + result.id()))
                .body(ReferenceIdResponse.from(result));
    }

    @GetMapping("/sources/{sourceId}/datasets")
    List<ReferenceDatasetResponse> datasets(
            @PathVariable UUID sourceId,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("reference.read");
        return listDatasets.handle(new ListReferenceDatasetsUseCase.Query(principal.requireBrewery(), sourceId))
                .stream().map(ReferenceDatasetResponse::from).toList();
    }

    @PostMapping("/sources/{sourceId}/datasets")
    ResponseEntity<ReferenceDatasetResponse> recordDataset(
            @PathVariable UUID sourceId,
            @Valid @RequestBody RecordReferenceDatasetRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("reference.manage");
        var result = recordDataset.handle(new RecordReferenceDatasetUseCase.Command(
                principal.userId(), principal.requireBrewery(), sourceId, request.datasetVersion(),
                request.rawPayload(), request.sourceSystem(), request.sourceRecordId(), request.sourceUrl(),
                request.retrievedAt(), request.effectiveFrom(), request.effectiveTo()));
        var body = ReferenceDatasetResponse.from(result);
        // Idempotência: conteúdo repetido devolve 200 com o dataset existente.
        return result.created()
                ? ResponseEntity.created(URI.create("/api/v1/reference/datasets/" + result.id())).body(body)
                : ResponseEntity.ok(body);
    }

    @PostMapping("/datasets/{datasetId}/publish")
    ReferenceDatasetResponse publishDataset(
            @PathVariable UUID datasetId,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("reference.publish");
        var result = publishDataset.handle(new PublishReferenceDatasetUseCase.Command(
                principal.userId(), principal.requireBrewery(), datasetId));
        return ReferenceDatasetResponse.from(result);
    }

    @PostMapping("/sources/{sourceId}/import-jobs")
    ResponseEntity<ImportJobResponse> submitJob(
            @PathVariable UUID sourceId,
            @Valid @RequestBody SubmitImportJobRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("reference.manage");
        var result = submitJob.handle(new SubmitImportJobUseCase.Command(
                principal.userId(), principal.requireBrewery(), sourceId, request.datasetVersion(),
                request.contentType(), request.rawPayload()));
        return ResponseEntity.created(URI.create("/api/v1/reference/import-jobs/" + result.jobId()))
                .body(ImportJobResponse.from(result));
    }

    @GetMapping("/sources/{sourceId}/import-jobs")
    List<ImportJobResponse> jobs(
            @PathVariable UUID sourceId,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("reference.read");
        return listJobs.handle(new ListImportJobsUseCase.Query(principal.requireBrewery(), sourceId))
                .stream().map(ImportJobResponse::from).toList();
    }

    @PostMapping("/import-jobs/{jobId}/publish")
    ImportJobResponse publishJob(
            @PathVariable UUID jobId,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("reference.publish");
        var result = publishJob.handle(new PublishImportJobUseCase.Command(
                principal.userId(), principal.requireBrewery(), jobId));
        return ImportJobResponse.from(result);
    }
}
