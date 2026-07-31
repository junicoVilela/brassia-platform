package br.com.brew.brassia.fermentation.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.fermentation.application.port.inbound.CollectYeastUseCase;
import br.com.brew.brassia.fermentation.application.port.outbound.YeastHarvestRepository;
import br.com.brew.brassia.fermentation.domain.YeastHarvest;
import br.com.brew.brassia.production.BatchLookup;
import java.util.Map;
import java.util.Objects;

/**
 * Registra uma coleta de levedura (YST-001). A geração vem da coleta-mãe (validada aqui), e
 * mãe reprovada não propaga linhagem: só levedura disponível pode gerar outra geração.
 */
public final class CollectYeastHandler implements CollectYeastUseCase {

    private final YeastHarvestRepository harvests;
    private final BatchLookup batches;
    private final AuditTrail audit;

    public CollectYeastHandler(YeastHarvestRepository harvests, BatchLookup batches, AuditTrail audit) {
        this.harvests = Objects.requireNonNull(harvests);
        this.batches = Objects.requireNonNull(batches);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        if (!batches.exists(command.breweryId(), command.sourceBatchId())) {
            throw new IllegalArgumentException("lote de origem inexistente: " + command.sourceBatchId());
        }
        if (harvests.existsByCode(command.breweryId(), command.code())) {
            throw new IllegalStateException("já existe coleta com o código " + command.code());
        }

        Integer parentGeneration = null;
        if (command.parentHarvestId() != null) {
            var parent = harvests.findById(command.breweryId(), command.parentHarvestId())
                    .orElseThrow(() -> new IllegalArgumentException("coleta-mãe inexistente"));
            if (!parent.available()) {
                throw new IllegalStateException("coleta-mãe não está disponível: " + parent.status());
            }
            parentGeneration = parent.generation();
        }

        var harvest = YeastHarvest.collect(command.breweryId(), command.code(), command.strainId(),
                command.sourceBatchId(), command.parentHarvestId(), parentGeneration, command.harvestedAt(),
                command.viabilityPercent(), command.condition(), command.storageLocation(),
                command.storageTempC());
        harvests.insert(harvest);

        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "fermentation.yeast.collect",
                "fermentation.yeast.harvest", harvest.id().toString(),
                Map.of("code", harvest.code(), "generation", String.valueOf(harvest.generation()),
                        "sourceBatchId", harvest.sourceBatchId().toString())));

        return new Result(harvest.id(), harvest.generation());
    }
}
