package br.com.brew.brassia.production.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.production.application.port.inbound.OpenBatchUseCase;
import br.com.brew.brassia.production.application.port.outbound.BatchRepository;
import br.com.brew.brassia.production.domain.Batch;
import br.com.brew.brassia.production.domain.BatchStep;
import br.com.brew.brassia.production.domain.BatchStepType;
import br.com.brew.brassia.recipe.RecipeLookup;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Abre o lote de produção (PRD-001): idempotente por OP, deriva o roteiro do dia
 * de brassa a partir dos estágios da receita publicada (mostura/fervura/whirlpool
 * /transferência) e persiste o Batch com o snapshot congelado.
 */
public final class OpenBatchHandler implements OpenBatchUseCase {

    private final BatchRepository repository;
    private final RecipeLookup recipes;
    private final AuditTrail audit;

    public OpenBatchHandler(BatchRepository repository, RecipeLookup recipes, AuditTrail audit) {
        this.repository = Objects.requireNonNull(repository);
        this.recipes = Objects.requireNonNull(recipes);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public void handle(Command command) {
        if (repository.existsByOrder(command.breweryId(), command.orderId())) {
            return; // idempotente: o lote da OP já foi aberto
        }

        var steps = deriveRoute(command.breweryId(), command.recipeId());
        var batch = Batch.open(command.breweryId(), command.orderId(), command.code(), command.recipeId(),
                command.recipeVersion(), command.recipeName(), command.volumeLiters(), Instant.now(),
                command.actorId(), steps);
        repository.insert(batch);

        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "production.batch.create",
                "production.batch", batch.id().value().toString(),
                Map.of("orderId", command.orderId().toString(), "steps", String.valueOf(steps.size()))));
    }

    private List<BatchStep> deriveRoute(java.util.UUID breweryId, java.util.UUID recipeId) {
        var composition = recipes.findPublishedComposition(breweryId, recipeId);
        boolean hasMash = true;
        boolean hasBoil = true;
        if (composition.isPresent()) {
            var stages = composition.get().items().stream()
                    .map(i -> i.stage() == null ? "" : i.stage().toUpperCase(Locale.ROOT))
                    .toList();
            hasMash = stages.contains("MASH");
            hasBoil = stages.contains("BOIL");
        }

        var steps = new ArrayList<BatchStep>();
        int seq = 1;
        if (hasMash) {
            steps.add(BatchStep.of(seq++, BatchStepType.MASH, "Mostura"));
        }
        if (hasBoil) {
            steps.add(BatchStep.of(seq++, BatchStepType.BOIL, "Fervura"));
            steps.add(BatchStep.of(seq++, BatchStepType.WHIRLPOOL, "Whirlpool"));
        }
        steps.add(BatchStep.of(seq, BatchStepType.TRANSFER, "Transferência ao fermentador"));
        return steps;
    }
}
