package br.com.brew.brassia.optimization.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.catalog.IngredientPurchaseLookup;
import br.com.brew.brassia.catalog.IngredientSpecLookup;
import br.com.brew.brassia.optimization.application.port.inbound.OptimizationCommands;
import br.com.brew.brassia.optimization.application.port.outbound.OptimizationRunRepository;
import br.com.brew.brassia.optimization.domain.ConstraintKind;
import br.com.brew.brassia.optimization.domain.Infeasible;
import br.com.brew.brassia.optimization.domain.OptimizationRun;
import br.com.brew.brassia.optimization.domain.SolverMethod;
import br.com.brew.brassia.optimization.domain.UnknownOptimizationRunException;
import br.com.brew.brassia.optimization.domain.UnpublishedRecipeException;
import br.com.brew.brassia.purchasing.IngredientSourcingLookup;
import br.com.brew.brassia.purchasing.StockOnHandLookup;
import br.com.brew.brassia.recipe.RecipeLookup;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Otimização assistida (OPT-001).
 *
 * <p><strong>A entrada é a versão publicada, e é isso que torna a corrida reproduzível.</strong> Otimizar
 * rascunho apontaria para uma composição que muda enquanto se otimiza. A versão da receita e a do catálogo
 * ficam gravadas com o resultado — sem elas, seis meses depois ninguém distingue "o solver mudou" de "o
 * preço do malte mudou".
 *
 * <p><strong>A explicação é comando separado.</strong> Gerá-la aqui dentro deixaria a fronteira dependendo
 * de disciplina de quem escreve o código; separada, ela não tem como entrar no cálculo.
 */
public final class OptimizationHandler implements OptimizationCommands {

    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);

    private final OptimizationRunRepository runs;
    private final RecipeLookup recipes;
    private final IngredientPurchaseLookup catalog;
    private final IngredientSpecLookup specs;
    private final IngredientSourcingLookup sourcing;
    private final StockOnHandLookup stock;
    private final SubstitutionSolver solver;
    private final AuditTrail audit;
    private final Clock clock;

    public OptimizationHandler(OptimizationRunRepository runs, RecipeLookup recipes,
            IngredientPurchaseLookup catalog, IngredientSpecLookup specs,
            IngredientSourcingLookup sourcing, StockOnHandLookup stock, AuditTrail audit, Clock clock) {
        this.runs = Objects.requireNonNull(runs, "runs");
        this.recipes = Objects.requireNonNull(recipes, "recipes");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.specs = Objects.requireNonNull(specs, "specs");
        this.sourcing = Objects.requireNonNull(sourcing, "sourcing");
        this.stock = Objects.requireNonNull(stock, "stock");
        this.solver = new SubstitutionSolver(specs);
        this.audit = Objects.requireNonNull(audit, "audit");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public OptimizationRun optimize(OptimizeCommand command) {
        Objects.requireNonNull(command, "command");
        var composition = recipes.findPublishedComposition(command.breweryId(), command.recipeId())
                .orElseThrow(() -> new UnpublishedRecipeException(
                        "a receita não tem versão publicada; otimizar rascunho produziria um resultado "
                                + "sobre uma composição que muda enquanto se otimiza"));
        var metrics = recipes.findPublishedForOrder(command.breweryId(), command.recipeId())
                .flatMap(RecipeLookup.PublishedForOrder::metrics);

        var costs = sourcing.preferredByIngredient(command.breweryId()).stream()
                .collect(Collectors.toMap(IngredientSourcingLookup.Sourcing::ingredientId,
                        IngredientSourcingLookup.Sourcing::unitCostPerCanonical, (a, b) -> a));
        var onHand = stock.onHandByIngredient(command.breweryId()).stream()
                .collect(Collectors.toMap(StockOnHandLookup.IngredientOnHand::ingredientId,
                        StockOnHandLookup.IngredientOnHand::onHand, (a, b) -> a));
        var items = catalog.findAll(command.breweryId());

        var context = new SubstitutionSolver.Context(command.breweryId(), composition, costs, onHand,
                items, originalCost(composition, costs),
                metrics.map(RecipeLookup.Metrics::ibu).orElse(null),
                metrics.map(RecipeLookup.Metrics::colorEbc).orElse(null));

        var candidates = solver.solve(context, command.objective(), command.constraints());
        var id = UUID.randomUUID();
        var now = clock.instant();
        // A versão do catálogo é derivada do próprio conteúdo lido: dois catálogos iguais dão a mesma
        // marca, e qualquer mudança de preço ou de item muda a marca. É o que permite dizer, depois, se
        // a entrada era a mesma.
        var catalogVersion = catalogVersionOf(items, costs);

        var run = candidates.isEmpty()
                ? OptimizationRun.infeasible(id, command.breweryId(), command.recipeId(),
                        composition.version(), command.objective(), command.constraints(),
                        SolverMethod.EXHAUSTIVE_SINGLE_SUBSTITUTION, catalogVersion, null,
                        infeasibilityOf(command), command.actor(), now)
                : OptimizationRun.solved(id, command.breweryId(), command.recipeId(),
                        composition.version(), command.objective(), command.constraints(),
                        SolverMethod.EXHAUSTIVE_SINGLE_SUBSTITUTION, catalogVersion, null,
                        candidates, command.actor(), now);
        runs.insert(run);

        var metadata = new LinkedHashMap<String, String>();
        metadata.put("objective", command.objective().name());
        metadata.put("method", run.method().name());
        metadata.put("catalogVersion", catalogVersion);
        metadata.put("recipeVersion", String.valueOf(composition.version()));
        metadata.put("feasible", String.valueOf(run.feasible()));
        metadata.put("candidates", String.valueOf(run.candidates().size()));
        record(command.breweryId(), command.actor(), "optimization.run.execute", id, metadata);
        return run;
    }

    @Override
    public OptimizationRun explain(UUID breweryId, UUID runId, String explanation, UUID actor) {
        var run = lockedOrFail(breweryId, runId);
        run.explain(explanation);
        runs.updateAnnotations(run);
        // Auditado como ato separado: a explicação é rastreável sem se confundir com o cálculo.
        record(breweryId, actor, "optimization.run.explain", runId, Map.of());
        return run;
    }

    @Override
    public OptimizationRun markApplied(UUID breweryId, UUID runId, UUID recipeVersionId, UUID actor) {
        var run = lockedOrFail(breweryId, runId);
        run.markApplied(recipeVersionId);
        runs.updateAnnotations(run);
        record(breweryId, actor, "optimization.run.apply", runId,
                Map.of("recipeVersionId", recipeVersionId.toString()));
        return run;
    }

    private OptimizationRun lockedOrFail(UUID breweryId, UUID runId) {
        return runs.findForUpdate(breweryId, runId)
                .orElseThrow(() -> new UnknownOptimizationRunException(runId));
    }

    /**
     * As restrições que se contradizem.
     *
     * <p>Nomeia as que efetivamente limitam a busca, porque "inviável" sozinho manda a pessoa afrouxar
     * tudo ao acaso. As de forma — manter e excluir ingrediente — entram junto: elas reduzem o espaço
     * antes de qualquer avaliação, e frequentemente são a causa real.
     */
    private static Infeasible infeasibilityOf(OptimizeCommand command) {
        var conflicting = command.constraints().stream().map(c -> c.kind().name()).distinct().toList();
        var explanation = conflicting.isEmpty()
                ? "Não há substituição possível: nenhum ingrediente do catálogo é do mesmo tipo dos "
                        + "usados nesta receita, ou eles não têm ficha técnica."
                : "Nenhuma combinação respeita ao mesmo tempo: " + String.join(", ", conflicting)
                        + ". Afrouxe uma delas ou amplie o catálogo.";
        return new Infeasible(conflicting, explanation);
    }

    private static BigDecimal originalCost(RecipeLookup.PublishedComposition composition,
            Map<UUID, BigDecimal> costs) {
        var total = composition.items().stream()
                .map(item -> costs.getOrDefault(item.ingredientId(), BigDecimal.ZERO)
                        .multiply(item.quantity(), MC))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(composition.batchVolumeLiters(), MC);
    }

    /**
     * Uma marca estável do catálogo lido.
     *
     * <p>Derivada do conteúdo e não do relógio: uma marca por data diria que a entrada mudou todo dia,
     * mesmo sem nada ter mudado — e perderia exatamente a informação que ela existe para dar.
     */
    private static String catalogVersionOf(List<IngredientPurchaseLookup.PurchaseSpec> items,
            Map<UUID, BigDecimal> costs) {
        var canonical = items.stream()
                .sorted(java.util.Comparator.comparing(s -> s.ingredientId().toString()))
                .map(s -> s.ingredientId() + "=" + costs.getOrDefault(s.ingredientId(), BigDecimal.ZERO)
                        .stripTrailingZeros().toPlainString())
                .collect(Collectors.joining(";"));
        return "catalog-" + Integer.toHexString(canonical.hashCode()) + "-" + items.size();
    }

    private void record(UUID breweryId, UUID actor, String action, UUID runId,
            Map<String, String> metadata) {
        audit.record(AuditEvent.success(breweryId, actor, action, "optimization_run",
                runId.toString(), metadata));
    }
}
