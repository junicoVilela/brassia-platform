package br.com.brew.brassia.production.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.equipment.EquipmentCapacityLookup;
import br.com.brew.brassia.equipment.EquipmentCleanlinessLookup;
import br.com.brew.brassia.equipment.EquipmentUsageCommands;
import br.com.brew.brassia.production.domain.DirtyEquipmentException;
import br.com.brew.brassia.production.application.port.inbound.TransferBatchUseCase;
import br.com.brew.brassia.production.application.port.outbound.BatchRepository;
import br.com.brew.brassia.production.application.port.outbound.TransferRepository;
import br.com.brew.brassia.production.domain.BatchStatus;
import br.com.brew.brassia.production.domain.BatchTransfer;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Transfere o lote ao fermentador (PRD-005): só lote em andamento; valida a
 * capacidade do fermentador destino e o balanço de massa (transferido + perdas ≤
 * volume do lote). Registra a transferência (única) e move o lote para FERMENTING.
 */
public final class TransferBatchHandler implements TransferBatchUseCase {

    private final BatchRepository batches;
    private final TransferRepository transfers;
    private final EquipmentCapacityLookup equipment;
    private final EquipmentCleanlinessLookup cleanliness;
    private final EquipmentUsageCommands usage;
    private final AuditTrail audit;

    public TransferBatchHandler(BatchRepository batches, TransferRepository transfers,
            EquipmentCapacityLookup equipment, EquipmentCleanlinessLookup cleanliness,
            EquipmentUsageCommands usage, AuditTrail audit) {
        this.batches = Objects.requireNonNull(batches);
        this.transfers = Objects.requireNonNull(transfers);
        this.equipment = Objects.requireNonNull(equipment);
        this.cleanliness = Objects.requireNonNull(cleanliness);
        this.usage = Objects.requireNonNull(usage);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public BatchTransfer handle(Command command) {
        var batch = batches.findById(command.breweryId(), command.batchId())
                .orElseThrow(() -> new IllegalArgumentException("lote inexistente"));
        if (batch.status() != BatchStatus.IN_PROGRESS) {
            throw new IllegalStateException("lote não está em andamento (já transferido ou encerrado)");
        }

        var capacity = equipment.capacityLiters(command.breweryId(), command.destinationEquipmentId())
                .orElseThrow(() -> new IllegalArgumentException("fermentador destino inexistente"));
        if (command.volumeLiters() != null && command.volumeLiters().compareTo(capacity) > 0) {
            throw new IllegalStateException("volume transferido excede a capacidade do fermentador");
        }

        // CLN-004-A: o fermentador sujo recusa a cerveja. Sem esta checagem, o estado de limpeza seria
        // decorativo — a plataforma afirmaria condicionar o uso à sanitização e só cumpriria isso no
        // envase, deixando o fermentador receber lote logo depois de esvaziar o anterior.
        var status = cleanliness.status(command.breweryId(), command.destinationEquipmentId());
        if (status.isPresent() && !status.get().clean()) {
            throw new DirtyEquipmentException(command.destinationEquipmentId(),
                    status.get().soiledSince());
        }

        var losses = command.lossesLiters() == null ? BigDecimal.ZERO : command.lossesLiters();
        var used = command.volumeLiters() == null ? BigDecimal.ZERO : command.volumeLiters().add(losses);
        if (used.compareTo(batch.volumeLiters()) > 0) {
            throw new IllegalStateException("balanço de massa: transferido + perdas excede o volume do lote");
        }

        var transfer = BatchTransfer.record(command.breweryId(), command.batchId(),
                command.destinationEquipmentId(), command.volumeLiters(), command.ogSg(), losses, Instant.now(),
                command.actorId());
        transfers.insert(transfer);

        if (!batches.markFermenting(command.breweryId(), command.batchId(), transfer.transferredAt())) {
            // Concorrência: transferência dupla; a UNIQUE (batch) + guarda de estado impedem efeito duplo.
            throw new IllegalStateException("lote não está em andamento (já transferido ou encerrado)");
        }

        // Recebeu cerveja: sujou. Depois de gravar a transferência, porque é ela que torna o uso um fato.
        usage.markSoiled(command.breweryId(), command.destinationEquipmentId(), transfer.transferredAt());

        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "production.batch.transfer",
                "production.batch", command.batchId().toString(),
                Map.of("destination", command.destinationEquipmentId().toString(),
                        "volume", transfer.volumeLiters().toPlainString())));

        return transfer;
    }
}
