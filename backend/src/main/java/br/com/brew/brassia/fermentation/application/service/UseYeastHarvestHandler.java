package br.com.brew.brassia.fermentation.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.fermentation.application.port.inbound.UseYeastHarvestUseCase;
import br.com.brew.brassia.fermentation.application.port.outbound.YeastHarvestRepository;
import br.com.brew.brassia.production.BatchLookup;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Confirma o uso de uma coleta num lote (YST-002). Exige confirmação explícita e lote de
 * destino existente; consome a coleta para a mesma levedura não ser pitchada duas vezes.
 * A recomendação nunca dispara isto sozinha.
 */
public final class UseYeastHarvestHandler implements UseYeastHarvestUseCase {

    private final YeastHarvestRepository harvests;
    private final BatchLookup batches;
    private final AuditTrail audit;

    public UseYeastHarvestHandler(YeastHarvestRepository harvests, BatchLookup batches, AuditTrail audit) {
        this.harvests = Objects.requireNonNull(harvests);
        this.batches = Objects.requireNonNull(batches);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public void handle(Command command) {
        if (!command.confirmed()) {
            throw new IllegalArgumentException("uso de levedura exige confirmação explícita");
        }
        if (!batches.exists(command.breweryId(), command.targetBatchId())) {
            throw new IllegalArgumentException("lote de destino inexistente: " + command.targetBatchId());
        }
        var harvest = harvests.findById(command.breweryId(), command.harvestId())
                .orElseThrow(() -> new IllegalArgumentException("coleta inexistente"));

        harvest.useIn(command.targetBatchId(), Instant.now());
        harvests.updatePitch(harvest);

        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "fermentation.yeast.use",
                "fermentation.yeast.harvest", harvest.id().toString(),
                Map.of("code", harvest.code(), "generation", String.valueOf(harvest.generation()),
                        "targetBatchId", command.targetBatchId().toString())));
    }
}
