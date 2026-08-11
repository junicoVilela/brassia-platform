package br.com.brew.brassia.production.adapter.inbound.gateway;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditOutcome;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.production.BlendResultCommands;
import br.com.brew.brassia.production.application.port.outbound.BatchRepository;
import br.com.brew.brassia.production.application.port.outbound.TransferRepository;
import br.com.brew.brassia.production.application.port.outbound.VolumeAdjustmentRepository;
import br.com.brew.brassia.production.domain.Batch;
import br.com.brew.brassia.production.domain.BatchTransfer;
import br.com.brew.brassia.recipe.RecipeLookup;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * A ponta da produção para o resultado de um blend (DEC-BLD-003).
 *
 * <p><strong>Não abre transação própria.</strong> Quem chama é a execução do blend, que já roda dentro de
 * uma — e é isso que garante o desfazimento inteiro: uma falha ao criar o segundo lote de uma divisão não
 * pode deixar o primeiro criado, com a operação marcada como executada e a conta sem fechar.
 *
 * <p><strong>A receita é conferida aqui, e precisa estar publicada.</strong> Um lote apontando para
 * rascunho de receita congelaria um snapshot que ainda vai mudar — e o rótulo imprimiria o que o rascunho
 * dizia no dia.
 */
@Component
class BlendResultCommandsAdapter implements BlendResultCommands {

    private final BatchRepository batches;
    private final TransferRepository transfers;
    private final VolumeAdjustmentRepository adjustments;
    private final RecipeLookup recipes;
    private final AuditTrail audit;

    BlendResultCommandsAdapter(BatchRepository batches, TransferRepository transfers,
            VolumeAdjustmentRepository adjustments, RecipeLookup recipes, AuditTrail audit) {
        this.batches = Objects.requireNonNull(batches, "batches");
        this.transfers = Objects.requireNonNull(transfers, "transfers");
        this.adjustments = Objects.requireNonNull(adjustments, "adjustments");
        this.recipes = Objects.requireNonNull(recipes, "recipes");
        this.audit = Objects.requireNonNull(audit, "audit");
    }

    @Override
    public UUID openBlendBatch(OpenBlendBatch command) {
        Objects.requireNonNull(command, "command");
        var recipe = recipes.findPublished(command.breweryId(), command.recipeId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "receita publicada inexistente: " + command.recipeId()));

        transfers.findFermentingBatchByEquipment(command.breweryId(), command.equipmentId())
                .ifPresent(occupant -> {
                    throw new VesselOccupiedException(command.equipmentId(), occupant);
                });

        var batch = Batch.openFromBlend(command.breweryId(), code(command), recipe.id(), recipe.version(),
                recipe.name(), command.liters(), command.occurredAt(), command.actorId());
        batches.insert(batch);
        // O enchimento entra como transferência porque é a transferência que diz onde o lote está. Sem ela
        // o lote existiria com volume e sem endereço: a telemetria não teria a quem se ligar e o tanque
        // continuaria aparecendo livre para o próximo.
        transfers.insert(BatchTransfer.fillFromBlend(command.breweryId(), batch.id().value(),
                command.equipmentId(), command.liters(), command.occurredAt(), command.actorId()));

        var metadata = new LinkedHashMap<String, String>();
        metadata.put("origin", batch.origin().name());
        metadata.put("blendOperationId", command.blendOperationId().toString());
        metadata.put("liters", command.liters().toPlainString());
        metadata.put("recipeId", recipe.id().toString());
        metadata.put("equipmentId", command.equipmentId().toString());
        audit.record(new AuditEvent(command.occurredAt(), command.breweryId(), command.actorId(),
                "production.batch.open-from-blend", "production.batch", batch.id().value().toString(),
                AuditOutcome.SUCCESS, metadata));
        return batch.id().value();
    }

    /**
     * O código carrega a operação que o produziu.
     *
     * <p>Quem vê "BLD-4f3a-1" numa tela de produção sabe, sem abrir nada, que aquele lote não veio de um
     * dia de brassa — e sabe onde procurar a operação que o criou. Um código sequencial comum faria o lote
     * de blend parecer um lote de brassa até alguém investigar.
     */
    private static String code(OpenBlendBatch command) {
        return "BLD-" + command.blendOperationId().toString().substring(0, 8) + "-" + command.outputSeq();
    }

    @Override
    public void adjustVolume(AdjustBatchVolume command) {
        Objects.requireNonNull(command, "command");
        var batch = batches.findById(command.breweryId(), command.batchId())
                .orElseThrow(() -> new IllegalArgumentException("lote inexistente: " + command.batchId()));

        var available = available(command.breweryId(), batch);
        var remaining = available.add(command.deltaLiters());
        if (remaining.signum() < 0) {
            // Tirar mais do que existe seria cerveja negativa num tanque. A recusa é na execução porque é
            // ela que move: entre a simulação e a execução, outro envase pode ter esvaziado o lote.
            throw new InsufficientBatchVolumeException(command.batchId(), available,
                    command.deltaLiters().abs());
        }

        var recorded = adjustments.insert(command.breweryId(), command.batchId(), command.deltaLiters(),
                "BLEND", command.blendOperationId(), command.actorId(), command.occurredAt());
        if (!recorded) {
            // A operação já ajustou este lote: repetir não acumula. Sair em silêncio é o comportamento
            // certo de uma execução repetida — o efeito pedido já está aplicado.
            return;
        }

        if (remaining.signum() == 0) {
            // Lote sem cerveja não é lote em fermentação: continuaria aparecendo como disponível para
            // envase, e o plano falharia lá na frente sem dizer que o tanque está vazio.
            batches.markCompleted(command.breweryId(), command.batchId(), command.occurredAt());
        }
    }

    /**
     * O que existe hoje no lote: o volume que a transferência determinou, mais os ajustes já feitos.
     *
     * <p>Mesma conta que o {@code BatchLookup} publica — e ela é conta, não coluna, para que corrigir uma
     * transferência corrija o saldo em vez de deixar dois números discordando.
     */
    private BigDecimal available(UUID breweryId, Batch batch) {
        var base = transfers.findByBatch(breweryId, batch.id().value())
                .map(transfer -> transfer.volumeLiters())
                .orElse(batch.volumeLiters());
        return base.add(adjustments.totalFor(breweryId, batch.id().value()));
    }
}
