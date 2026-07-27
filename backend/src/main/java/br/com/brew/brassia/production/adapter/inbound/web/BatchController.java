package br.com.brew.brassia.production.adapter.inbound.web;

import br.com.brew.brassia.production.adapter.inbound.web.dto.BatchView;
import br.com.brew.brassia.production.adapter.inbound.web.dto.MeasurementView;
import br.com.brew.brassia.production.adapter.inbound.web.dto.RecordMeasurementRequest;
import br.com.brew.brassia.production.application.port.inbound.CompleteBatchStepUseCase;
import br.com.brew.brassia.production.application.port.inbound.GetBatchUseCase;
import br.com.brew.brassia.production.application.port.inbound.ListBatchesUseCase;
import br.com.brew.brassia.production.application.port.inbound.ListMeasurementsUseCase;
import br.com.brew.brassia.production.application.port.inbound.RecordMeasurementUseCase;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/production/batches")
final class BatchController {

    private final ListBatchesUseCase listBatches;
    private final GetBatchUseCase getBatch;
    private final CompleteBatchStepUseCase completeStep;
    private final RecordMeasurementUseCase recordMeasurement;
    private final ListMeasurementsUseCase listMeasurements;

    BatchController(ListBatchesUseCase listBatches, GetBatchUseCase getBatch,
            CompleteBatchStepUseCase completeStep, RecordMeasurementUseCase recordMeasurement,
            ListMeasurementsUseCase listMeasurements) {
        this.listBatches = listBatches;
        this.getBatch = getBatch;
        this.completeStep = completeStep;
        this.recordMeasurement = recordMeasurement;
        this.listMeasurements = listMeasurements;
    }

    @GetMapping
    List<BatchView> list(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("production.batch.read");
        return listBatches.handle(principal.requireBrewery()).stream().map(BatchView::from).toList();
    }

    @GetMapping("/{id}")
    BatchView get(@PathVariable UUID id, @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("production.batch.read");
        return BatchView.from(getBatch.handle(principal.requireBrewery(), id));
    }

    @PostMapping("/{id}/steps/{stepId}/complete")
    BatchView completeStep(
            @PathVariable UUID id, @PathVariable UUID stepId,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("production.batch.manage");
        return BatchView.from(completeStep.handle(new CompleteBatchStepUseCase.Command(
                principal.userId(), principal.requireBrewery(), id, stepId)));
    }

    @PostMapping("/{id}/measurements")
    ResponseEntity<RecordedResponse> recordMeasurement(
            @PathVariable UUID id, @Valid @RequestBody RecordMeasurementRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("production.measurement.record");
        var result = recordMeasurement.handle(new RecordMeasurementUseCase.Command(
                principal.userId(), principal.requireBrewery(), id, request.stepId(), request.kind(),
                request.value(), request.unit(), request.temperatureC(), request.method(), request.source()));
        return ResponseEntity.created(
                URI.create("/api/v1/production/batches/" + id + "/measurements/" + result.id()))
                .body(new RecordedResponse(result.id()));
    }

    record RecordedResponse(UUID id) {}

    @GetMapping("/{id}/measurements")
    List<MeasurementView> measurements(@PathVariable UUID id, @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("production.batch.read");
        return listMeasurements.handle(principal.requireBrewery(), id).stream().map(MeasurementView::from).toList();
    }
}
