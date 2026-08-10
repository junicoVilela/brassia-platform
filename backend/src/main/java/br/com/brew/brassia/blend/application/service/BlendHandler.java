package br.com.brew.brassia.blend.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.blend.application.port.inbound.BlendCommands;
import br.com.brew.brassia.blend.application.port.outbound.BlendRepository;
import br.com.brew.brassia.blend.domain.BlendOperation;
import br.com.brew.brassia.blend.domain.UnknownBlendBatchException;
import br.com.brew.brassia.blend.domain.UnknownBlendOperationException;
import br.com.brew.brassia.blend.domain.VolumeMovement;
import br.com.brew.brassia.production.BatchLookup;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * União e divisão de volume (BLD-001).
 *
 * <p><strong>Os lotes são conferidos na simulação.</strong> Simular é o único momento barato: depois de
 * aprovar, descobrir que o lote de destino não existe é descobrir tarde para uma operação irreversível —
 * duas cervejas misturadas não se separam.
 */
public final class BlendHandler implements BlendCommands {

    private final BlendRepository operations;
    private final BatchLookup batches;
    private final AuditTrail audit;
    private final Clock clock;

    public BlendHandler(BlendRepository operations, BatchLookup batches, AuditTrail audit, Clock clock) {
        this.operations = Objects.requireNonNull(operations, "operations");
        this.batches = Objects.requireNonNull(batches, "batches");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public BlendOperation simulate(SimulateCommand command) {
        Objects.requireNonNull(command, "command");
        var inputs = movements(command.breweryId(), command.inputs(), "origem");
        var outputs = movements(command.breweryId(), command.outputs(), "destino");

        var operation = BlendOperation.simulate(UUID.randomUUID(), command.breweryId(), command.kind(),
                inputs, outputs, command.declaredLossLiters(), command.reason(), command.actor(),
                clock.instant());
        operations.insert(operation);

        var metadata = new LinkedHashMap<String, String>();
        metadata.put("kind", operation.kind().name());
        metadata.put("inputLiters", operation.inputLiters().toPlainString());
        metadata.put("outputLiters", operation.outputLiters().toPlainString());
        metadata.put("declaredLossLiters", operation.declaredLossLiters().toPlainString());
        record(command.breweryId(), command.actor(), "blend.operation.simulate", operation, metadata);
        return operation;
    }

    @Override
    public BlendOperation approve(UUID breweryId, UUID operationId, UUID actor) {
        var operation = lockedOrFail(breweryId, operationId);
        operation.approve(actor, clock.instant());
        operations.updateProgress(operation);
        record(breweryId, actor, "blend.operation.approve", operation, Map.of());
        return operation;
    }

    /**
     * Executa.
     *
     * <p>É aqui que a genealogia passa a valer: a partir da execução, o recall que alcança um dos lotes
     * alcança o outro. Antes disso nenhuma cerveja se tocou, e uma aresta prematura faria o recall
     * exagerar — o que o leva a ser descartado por quem o recebe.
     */
    @Override
    public BlendOperation execute(UUID breweryId, UUID operationId, UUID actor) {
        var operation = lockedOrFail(breweryId, operationId);
        operation.execute(actor, clock.instant());
        operations.updateProgress(operation);

        var metadata = new LinkedHashMap<String, String>();
        metadata.put("inputBatches", ids(operation.inputs()));
        metadata.put("outputBatches", ids(operation.outputs()));
        record(breweryId, actor, "blend.operation.execute", operation, metadata);
        return operation;
    }

    @Override
    public BlendOperation discard(UUID breweryId, UUID operationId, UUID actor) {
        var operation = lockedOrFail(breweryId, operationId);
        operation.discard();
        operations.updateProgress(operation);
        record(breweryId, actor, "blend.operation.discard", operation, Map.of());
        return operation;
    }

    private BlendOperation lockedOrFail(UUID breweryId, UUID operationId) {
        // Sem o FOR UPDATE, duas execuções simultâneas leriam o mesmo APPROVED e ambas passariam pela
        // verificação de estado — e a cerveja seria movida duas vezes.
        return operations.findForUpdate(breweryId, operationId)
                .orElseThrow(() -> new UnknownBlendOperationException(operationId));
    }

    private List<VolumeMovement> movements(UUID breweryId, List<MovementInput> inputs, String side) {
        return inputs.stream().map(input -> {
            if (!batches.exists(breweryId, input.batchId())) {
                throw new UnknownBlendBatchException(
                        "lote de " + side + " não existe nesta cervejaria: " + input.batchId());
            }
            return new VolumeMovement(input.batchId(), input.liters());
        }).toList();
    }

    private static String ids(List<VolumeMovement> movements) {
        return movements.stream().map(m -> m.batchId().toString()).reduce((a, b) -> a + "," + b)
                .orElse("");
    }

    private void record(UUID breweryId, UUID actor, String action, BlendOperation operation,
            Map<String, String> metadata) {
        audit.record(AuditEvent.success(breweryId, actor, action, "blend_operation",
                operation.id().toString(), metadata));
    }
}
