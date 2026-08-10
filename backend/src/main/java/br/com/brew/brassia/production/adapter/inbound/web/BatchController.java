package br.com.brew.brassia.production.adapter.inbound.web;

import br.com.brew.brassia.production.adapter.inbound.web.dto.BatchView;
import br.com.brew.brassia.production.adapter.inbound.web.dto.BrewConsumptionDtos;
import br.com.brew.brassia.production.adapter.inbound.web.dto.MeasurementView;
import br.com.brew.brassia.production.adapter.inbound.web.dto.RecordMeasurementRequest;
import br.com.brew.brassia.production.adapter.inbound.web.dto.TransferRequest;
import br.com.brew.brassia.production.adapter.inbound.web.dto.TransferView;
import br.com.brew.brassia.production.application.port.inbound.BrewConsumptionUseCases;
import br.com.brew.brassia.production.application.port.inbound.CompleteBatchStepUseCase;
import br.com.brew.brassia.production.application.port.inbound.GetBatchTransferUseCase;
import br.com.brew.brassia.production.application.port.inbound.GetBatchUseCase;
import br.com.brew.brassia.production.application.port.inbound.ListBatchesUseCase;
import br.com.brew.brassia.production.application.port.inbound.ListMeasurementsUseCase;
import br.com.brew.brassia.production.application.port.inbound.RecordMeasurementUseCase;
import br.com.brew.brassia.production.application.port.inbound.TransferBatchUseCase;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import br.com.brew.brassia.shared.web.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
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
@RequestMapping("/api/v1/production/batches")
final class BatchController {

    private final ListBatchesUseCase listBatches;
    private final GetBatchUseCase getBatch;
    private final CompleteBatchStepUseCase completeStep;
    private final RecordMeasurementUseCase recordMeasurement;
    private final ListMeasurementsUseCase listMeasurements;
    private final TransferBatchUseCase transferBatch;
    private final GetBatchTransferUseCase getTransfer;
    private final BrewConsumptionUseCases.Proposal consumptionProposal;
    private final BrewConsumptionUseCases.Register registerConsumption;

    BatchController(ListBatchesUseCase listBatches, GetBatchUseCase getBatch,
            CompleteBatchStepUseCase completeStep, RecordMeasurementUseCase recordMeasurement,
            ListMeasurementsUseCase listMeasurements, TransferBatchUseCase transferBatch,
            GetBatchTransferUseCase getTransfer, BrewConsumptionUseCases.Proposal consumptionProposal,
            BrewConsumptionUseCases.Register registerConsumption) {
        this.listBatches = listBatches;
        this.getBatch = getBatch;
        this.completeStep = completeStep;
        this.recordMeasurement = recordMeasurement;
        this.listMeasurements = listMeasurements;
        this.transferBatch = transferBatch;
        this.getTransfer = getTransfer;
        this.consumptionProposal = consumptionProposal;
        this.registerConsumption = registerConsumption;
    }

    /**
     * Lista os lotes, paginado (REL-002).
     *
     * <p><strong>Mudança incompatível, feita agora de propósito.</strong> A resposta deixou de ser um
     * array e passou a ser um envelope com {@code content}. A listagem sem limite crescia com o histórico
     * — 300 lotes em 40 ms, 3.000 em 319 ms — e cruzaria a meta de 500 ms por volta de 4.700 lotes.
     *
     * <p>Quebrar o contrato só é barato antes da primeira produção, que é onde o projeto está
     * (0.1.0-SNAPSHOT, Sprint 17). Depois do primeiro cliente integrado, a mesma correção exigiria versão
     * nova e janela de transição (`docs/20_RELEASE_MIGRATION.md`) — o custo é dez vezes maior por adiar.
     */
    @GetMapping
    PageResponse<BatchView> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("production.batch.read");
        var result = listBatches.handle(
                new ListBatchesUseCase.Query(principal.requireBrewery(), page, size));
        var content = result.content().stream().map(BatchView::from).toList();
        return new PageResponse<>(content, result.page(), result.size(), result.totalElements(),
                result.totalPages());
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
                request.value(), request.unit(), request.temperatureC(), request.method(), request.source(),
                request.clientRequestId()));

        // 200 para apontamento repetido, 201 para novo (PWA-002). A distinção é a resposta certa a uma fila
        // que reenviou por não ter recebido a confirmação: ela fez o correto, e um erro a ensinaria a
        // continuar tentando. O 200 diz "está registrado, pode tirar da fila" sem criar segunda medição.
        if (result.duplicate()) {
            return ResponseEntity.ok(new RecordedResponse(result.id(), true));
        }
        return ResponseEntity.created(
                URI.create("/api/v1/production/batches/" + id + "/measurements/" + result.id()))
                .body(new RecordedResponse(result.id(), false));
    }

    record RecordedResponse(UUID id, boolean duplicate) {}

    @GetMapping("/{id}/measurements")
    List<MeasurementView> measurements(@PathVariable UUID id, @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("production.batch.read");
        return listMeasurements.handle(principal.requireBrewery(), id).stream().map(MeasurementView::from).toList();
    }

    @PostMapping("/{id}/transfer")
    @ResponseStatus(HttpStatus.CREATED)
    TransferView transfer(
            @PathVariable UUID id, @Valid @RequestBody TransferRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("production.batch.manage");
        var transfer = transferBatch.handle(new TransferBatchUseCase.Command(
                principal.userId(), principal.requireBrewery(), id, request.destinationEquipmentId(),
                request.volumeLiters(), request.ogSg(), request.lossesLiters()));
        return TransferView.from(transfer);
    }

    /**
     * O que a OP separou, para o operador confirmar ou corrigir antes de virar consumo (TRC-001-C).
     */
    @GetMapping("/{id}/consumption/proposal")
    BrewConsumptionDtos.ProposalView consumptionProposal(@PathVariable UUID id,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("production.batch.read");
        return BrewConsumptionDtos.ProposalView.from(
                consumptionProposal.handle(principal.requireBrewery(), id));
    }

    /**
     * Registra o consumo do dia de brassa, lote a lote. É o que transforma a reserva — intenção —
     * no fato de que aquele malte entrou nesta cerveja.
     */
    @PostMapping("/{id}/consumption")
    void registerConsumption(@PathVariable UUID id,
            @Valid @RequestBody BrewConsumptionDtos.RegisterRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("production.batch.manage");
        registerConsumption.handle(new BrewConsumptionUseCases.Register.Command(
                principal.requireBrewery(), principal.userId(), id,
                request.lines().stream()
                        .map(line -> new BrewConsumptionUseCases.Register.Line(line.lotId(),
                                line.quantity(), line.unit()))
                        .toList()));
    }

    @GetMapping("/{id}/transfer")
    ResponseEntity<TransferView> transfer(@PathVariable UUID id,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("production.batch.read");
        return getTransfer.handle(principal.requireBrewery(), id)
                .map(t -> ResponseEntity.ok(TransferView.from(t)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
