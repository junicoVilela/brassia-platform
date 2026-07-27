package br.com.brew.brassia.production.adapter.inbound.web;

import br.com.brew.brassia.production.adapter.inbound.web.dto.BatchView;
import br.com.brew.brassia.production.application.port.inbound.CompleteBatchStepUseCase;
import br.com.brew.brassia.production.application.port.inbound.GetBatchUseCase;
import br.com.brew.brassia.production.application.port.inbound.ListBatchesUseCase;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/production/batches")
final class BatchController {

    private final ListBatchesUseCase listBatches;
    private final GetBatchUseCase getBatch;
    private final CompleteBatchStepUseCase completeStep;

    BatchController(ListBatchesUseCase listBatches, GetBatchUseCase getBatch,
            CompleteBatchStepUseCase completeStep) {
        this.listBatches = listBatches;
        this.getBatch = getBatch;
        this.completeStep = completeStep;
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
}
