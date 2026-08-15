package br.com.brew.brassia.costing.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.brew.brassia.catalog.IngredientPurchaseLookup;
import br.com.brew.brassia.costing.MaterialActualSource;
import br.com.brew.brassia.costing.MaterialActualSource.Actuals;
import br.com.brew.brassia.costing.MaterialActualSource.MaterialFact;
import br.com.brew.brassia.costing.domain.BatchVariance;
import br.com.brew.brassia.costing.domain.UnknownBatchCostException;
import br.com.brew.brassia.packaging.PackagingOutcomeLookup;
import br.com.brew.brassia.planning.OrderPlanLookup;
import br.com.brew.brassia.planning.OrderPlanLookup.OrderPlan;
import br.com.brew.brassia.planning.OrderPlanLookup.PlannedMaterial;
import br.com.brew.brassia.production.BatchLookup;
import br.com.brew.brassia.recipe.RecipeLookup;
import br.com.brew.brassia.production.BatchOutcomeLookup;
import br.com.brew.brassia.production.BatchOutcomeLookup.BatchOutcome;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Como o plano encontra o fato — e quando ele se recusa a encontrar (CST-002). */
class BatchVarianceAssemblerTest {

    private static final UUID BREWERY = UUID.randomUUID();
    private static final UUID BATCH = UUID.randomUUID();
    private static final UUID ORDER = UUID.randomUUID();
    private static final UUID MALTE = UUID.randomUUID();
    private static final UUID LUPULO = UUID.randomUUID();

    @Test
    @DisplayName("plano e fato se casam por ingrediente, e a conta fecha ponta a ponta")
    void casaPlanoEFato() {
        var variance = assemble(plan(2, planned(MALTE, "20")),
                actuals(List.of(fact(MALTE, "20", "100")), List.of(fact(MALTE, "22", "121"))),
                transferred());

        assertThat(variance.compared()).hasSize(1);
        assertThat(variance.consumptionVariance()).isEqualByComparingTo("10");
        assertThat(variance.priceVariance()).isEqualByComparingTo("11");
        assertThat(variance.reconciles()).isTrue();
    }

    @Test
    @DisplayName("receita republicada depois da ordem não vira base: declara em vez de comparar")
    void receitaRepublicadaNaoServeDeBase() {
        // Ordem congelou a versão 2; a publicada hoje é a 3.
        var variance = assemble(new OrderPlan(new BigDecimal("400"), 2, 3, List.of(planned(MALTE, "30"))),
                actuals(List.of(fact(MALTE, "20", "100")), List.of(fact(MALTE, "22", "121"))),
                transferred());

        assertThat(reasons(variance)).anyMatch(reason -> reason.contains("republicada"));
        // Sem plano confiável não há variação de consumo: a diferença seria entre duas receitas.
        assertThat(variance.materials()).allMatch(material -> material.plannedQuantity() == null);
        assertThat(variance.consumptionVariance()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("receita fora de publicação também não serve de base")
    void receitaDespublicadaNaoServeDeBase() {
        var variance = assemble(new OrderPlan(new BigDecimal("400"), 2, null, List.of()),
                actuals(List.of(fact(MALTE, "20", "100")), List.of(fact(MALTE, "22", "121"))),
                transferred());

        assertThat(reasons(variance)).anyMatch(reason -> reason.contains("não está mais publicada"));
    }

    @Test
    @DisplayName("insumo que a ordem não separou vira lacuna nominal, não variação de preço")
    void insumoSemReservaViraLacuna() {
        var variance = assemble(plan(2, planned(MALTE, "20"), planned(LUPULO, "0.5")),
                actuals(List.of(fact(MALTE, "20", "100")),
                        List.of(fact(MALTE, "20", "100"), fact(LUPULO, "0.5", "60"))),
                transferred());

        assertThat(reasons(variance)).anyMatch(reason -> reason.contains("não separou lote"));
        assertThat(variance.compared()).hasSize(1);
        assertThat(variance.actualCost()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("insumo planejado e não usado é desvio de consumo, não desvio de preço")
    void planejadoENaoUsado() {
        var variance = assemble(plan(2, planned(MALTE, "20"), planned(LUPULO, "0.5")),
                actuals(List.of(fact(MALTE, "20", "100"), fact(LUPULO, "0.5", "60")),
                        List.of(fact(MALTE, "20", "100"))),
                transferred());

        var lupulo = variance.materials().stream()
                .filter(material -> material.ingredientId().equals(LUPULO))
                .findFirst().orElseThrow();
        assertThat(lupulo.actualQuantity()).isEqualByComparingTo("0");
        assertThat(lupulo.consumptionVariance()).isEqualByComparingTo("-60");
        assertThat(lupulo.priceVariance()).isEqualByComparingTo("0");
        assertThat(variance.reconciles()).isTrue();
    }

    @Test
    @DisplayName("sem consumo confirmado, o real é vazio e não zero")
    void semConsumoORealNaoExiste() {
        var variance = assemble(plan(2, planned(MALTE, "20")),
                actuals(List.of(fact(MALTE, "20", "100")), List.of()), transferred());

        assertThat(reasons(variance)).anyMatch(reason -> reason.contains("ainda não foi confirmado"));
        assertThat(variance.materials()).allMatch(material -> material.actualQuantity() == null);
        assertThat(variance.compared()).isEmpty();
    }

    @Test
    @DisplayName("lote ainda não transferido não tem rendimento — e o relatório diz isso")
    void semTransferenciaNaoHaRendimento() {
        var variance = assemble(plan(2, planned(MALTE, "20")),
                actuals(List.of(fact(MALTE, "20", "100")), List.of(fact(MALTE, "20", "100"))),
                new BatchOutcome(new BigDecimal("400"), null, null));

        assertThat(variance.volumes()).noneMatch(volume -> volume.kind() == BatchVariance.VolumeKind.YIELD);
        assertThat(reasons(variance)).anyMatch(reason -> reason.contains("ainda não foi transferido"));
    }

    @Test
    @DisplayName("a perda entra como fato e declara que não tem esperado (CST-002-A)")
    void perdaEntraSemBase() {
        var variance = assemble(plan(2, planned(MALTE, "20")),
                actuals(List.of(fact(MALTE, "20", "100")), List.of(fact(MALTE, "20", "100"))),
                transferred());

        var perda = variance.volumes().stream()
                .filter(volume -> volume.kind() == BatchVariance.VolumeKind.LOSS)
                .findFirst().orElseThrow();
        assertThat(perda.actual()).isEqualByComparingTo("8");
        assertThat(perda.comparable()).isFalse();
        assertThat(reasons(variance)).anyMatch(reason -> reason.contains("não define perda esperada"));
    }

    @Test
    @DisplayName("COM PERDA ESPERADA NA RECEITA, A PERDA VIRA DESVIO — e o esperado sai do planejado")
    void perdaComparaContraOEsperado() {
        // CST-002-A. 2% de 400 L planejados são 8 L esperados; a perda real foi 8 L, então não há desvio.
        // O esperado incide sobre o PLANEJADO: calculá-lo sobre o realizado faria o esperado seguir o
        // desvio, e um lote que rendeu menos "esperaria" perder menos — o desvio sumiria por construção.
        var variance = assembleWithExpectedLoss(new BigDecimal("2"), null);

        var perda = variance.volumes().stream()
                .filter(volume -> volume.kind() == BatchVariance.VolumeKind.LOSS)
                .findFirst().orElseThrow();
        assertThat(perda.comparable()).isTrue();
        assertThat(perda.planned()).isEqualByComparingTo("8");
        assertThat(perda.actual()).isEqualByComparingTo("8");
        assertThat(reasons(variance)).noneMatch(reason -> reason.contains("não define perda esperada"));
    }

    @Test
    @DisplayName("perder MENOS que o esperado é favorável, e o relatório diz isso por si")
    void perderMenosEhFavoravel() {
        // Em volume o sinal sozinho não basta: render 10 L a menos é ruim, perder 2 L a menos é bom.
        var variance = assembleWithExpectedLoss(new BigDecimal("5"), null);

        var perda = variance.volumes().stream()
                .filter(volume -> volume.kind() == BatchVariance.VolumeKind.LOSS)
                .findFirst().orElseThrow();
        assertThat(perda.planned()).isEqualByComparingTo("20");
        assertThat(perda.actual()).isEqualByComparingTo("8");
        assertThat(perda.unfavorable()).isFalse();
    }

    @Test
    @DisplayName("lote inexistente é recusado em vez de devolver variação vazia")
    void loteInexistenteEhRecusado() {
        var assembler = assembler(batchId -> Optional.empty(), plan(2, planned(MALTE, "20")),
                Actuals.empty(), transferred(), List.of());

        assertThatThrownBy(() -> assembler.assemble(BREWERY, BATCH))
                .isInstanceOf(UnknownBatchCostException.class);
    }

    // --- cenário ---

    /** O mesmo cenário, com a receita declarando quanto se admite perder (CST-002-A). */
    private static BatchVariance assembleWithExpectedLoss(BigDecimal transferPercent,
            BigDecimal packagingPercent) {
        return assembler(batchId -> Optional.of(snapshot()), plan(2, planned(MALTE, "20")),
                actuals(List.of(fact(MALTE, "20", "100")), List.of(fact(MALTE, "20", "100"))),
                transferred(), List.of(),
                new RecipeLookup.ExpectedLoss(transferPercent, packagingPercent))
                .assemble(BREWERY, BATCH);
    }

    private static BatchVariance assemble(OrderPlan plan, Actuals actuals, BatchOutcome outcome) {
        return assembler(batchId -> Optional.of(snapshot()), plan, actuals, outcome, List.of())
                .assemble(BREWERY, BATCH);
    }

    private static BatchVarianceAssembler assembler(java.util.function.Function<UUID,
            Optional<BatchLookup.Snapshot>> batches, OrderPlan plan, Actuals actuals,
            BatchOutcome outcome, List<PackagingOutcomeLookup.PackagingOutcome> runs) {
        return assembler(batches, plan, actuals, outcome, runs, null);
    }

    private static BatchVarianceAssembler assembler(java.util.function.Function<UUID,
            Optional<BatchLookup.Snapshot>> batches, OrderPlan plan, Actuals actuals,
            BatchOutcome outcome, List<PackagingOutcomeLookup.PackagingOutcome> runs,
            RecipeLookup.ExpectedLoss expectedLoss) {
        BatchLookup lookup = (breweryId, batchId) -> batches.apply(batchId);
        BatchOutcomeLookup outcomes = (breweryId, batchId) -> Optional.of(outcome);
        OrderPlanLookup plans = (breweryId, orderId) -> Optional.ofNullable(plan);
        MaterialActualSource source = (breweryId, orderId) -> actuals;
        PackagingOutcomeLookup packaging = (breweryId, batchId) -> runs;
        IngredientPurchaseLookup ingredients = breweryId -> List.of();
        // Perda esperada nula é o estado de quem ainda não mediu a própria — a perda como fato, sem desvio.
        RecipeLookup recipes = new RecipeLookup() {
            @Override
            public java.util.Optional<ExpectedLoss> expectedLoss(UUID breweryId, UUID recipeId) {
                return java.util.Optional.ofNullable(expectedLoss);
            }

            @Override
            public java.util.Optional<PublishedRecipe> findPublished(UUID breweryId, UUID recipeId) {
                return java.util.Optional.empty();
            }

            @Override
            public java.util.Optional<PublishedComposition> findPublishedComposition(UUID breweryId,
                    UUID recipeId) {
                return java.util.Optional.empty();
            }

            @Override
            public java.util.Optional<PublishedForOrder> findPublishedForOrder(UUID breweryId,
                    UUID recipeId) {
                return java.util.Optional.empty();
            }
        };
        return new BatchVarianceAssembler(lookup, outcomes, plans, source, packaging, ingredients,
                recipes);
    }

    private static BatchLookup.Snapshot snapshot() {
        return new BatchLookup.Snapshot(BATCH, ORDER, "LOTE-100", new BigDecimal("400"),
                new BigDecimal("390"), "FERMENTING", UUID.randomUUID(), 2, "IPA");
    }

    private static BatchOutcome transferred() {
        return new BatchOutcome(new BigDecimal("400"), new BigDecimal("390"), new BigDecimal("8"));
    }

    private static OrderPlan plan(int version, PlannedMaterial... materials) {
        return new OrderPlan(new BigDecimal("400"), version, version, List.of(materials));
    }

    private static PlannedMaterial planned(UUID ingredientId, String quantity) {
        return new PlannedMaterial(ingredientId, new BigDecimal(quantity), "KG");
    }

    private static Actuals actuals(List<MaterialFact> reserved, List<MaterialFact> consumed) {
        return new Actuals(reserved, consumed);
    }

    private static MaterialFact fact(UUID ingredientId, String quantity, String totalCost) {
        return new MaterialFact(ingredientId, "insumo", new BigDecimal(quantity), "KG",
                new BigDecimal(totalCost));
    }

    private static List<String> reasons(BatchVariance variance) {
        return variance.gaps().stream().map(BatchVariance.VarianceGap::reason).toList();
    }
}
