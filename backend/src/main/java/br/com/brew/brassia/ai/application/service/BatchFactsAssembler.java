package br.com.brew.brassia.ai.application.service;

import br.com.brew.brassia.ai.domain.Fact;
import br.com.brew.brassia.costing.BatchCostLookup;
import br.com.brew.brassia.fermentation.FermentationLookup;
import br.com.brew.brassia.production.BatchLookup;
import br.com.brew.brassia.production.BatchOutcomeLookup;
import br.com.brew.brassia.quality.BatchQualityLookup;
import br.com.brew.brassia.recipe.RecipeLookup;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Reúne os fatos determinísticos de um lote (AIA-002).
 *
 * <p><strong>Cada número vem de quem responde por ele.</strong> Volume e estado vêm da produção; OG, FG, ABV
 * e IBU vêm do motor de cálculo da receita; medições e desvios vêm da qualidade; custo vem do custeio.
 * Nenhum é recalculado aqui — recalcular criaria uma segunda opinião sobre o mesmo fato, e duas opiniões
 * sobre o mesmo fato divergem. O que este montador faz é perguntar a cada dono e etiquetar a resposta.
 *
 * <p><strong>Os derivados existem para que o modelo não faça conta.</strong> Perda percentual e proporção
 * fora da faixa poderiam ser calculadas pelo modelo a partir de dois fatos — e aí o resultado seria conta de
 * quem não presta contas dela. Calculadas aqui, são fato como qualquer outro, com origem declarada.
 *
 * <p><strong>Ausência é fato.</strong> "Não houve transferência" e "ninguém mediu" viajam como fato ausente,
 * não como zero: um zero no lugar da ausência faria o modelo ler um lote parado como um lote perfeito, e um
 * lote não medido como um lote aprovado.
 *
 * <p><strong>A fermentação entra desde DEB-AIA-001.</strong> Antes ela ficava de fora porque o módulo não
 * publicava consulta no pacote raiz, e a avaliação julgava um lote sem ver curva, agenda nem levedura — que
 * é onde mora boa parte do risco. Um lote com três etapas atrasadas e densidade parada há dois dias
 * chegava ao modelo idêntico a um lote saudável.
 */
public final class BatchFactsAssembler {

    /** Casas do percentual: décimo de ponto é o quanto se discute uma perda de brassagem. */
    private static final int PERCENT_SCALE = 1;

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final BatchLookup batches;
    private final BatchOutcomeLookup outcomes;
    private final BatchQualityLookup quality;
    private final BatchCostLookup costs;
    private final RecipeLookup recipes;
    private final FermentationLookup fermentation;

    public BatchFactsAssembler(BatchLookup batches, BatchOutcomeLookup outcomes,
            BatchQualityLookup quality, BatchCostLookup costs, RecipeLookup recipes,
            FermentationLookup fermentation) {
        this.batches = Objects.requireNonNull(batches);
        this.outcomes = Objects.requireNonNull(outcomes);
        this.quality = Objects.requireNonNull(quality);
        this.costs = Objects.requireNonNull(costs);
        this.recipes = Objects.requireNonNull(recipes);
        this.fermentation = Objects.requireNonNull(fermentation);
    }

    /**
     * @return os fatos do lote, ou vazio quando o lote não existe nesta cervejaria
     */
    public Optional<BatchFacts> of(UUID breweryId, UUID batchId) {
        return batches.find(breweryId, batchId).map(batch -> {
            var facts = new ArrayList<Fact>();
            addBatch(facts, batch);
            addRecipeMetrics(facts, breweryId, batch);
            addOutcome(facts, breweryId, batchId, batch);
            addQuality(facts, breweryId, batchId);
            addFermentation(facts, breweryId, batchId);
            addCost(facts, breweryId, batchId);
            return new BatchFacts(batch.code(), batch.recipeName(), batch.recipeVersion(),
                    batch.status(), List.copyOf(facts));
        });
    }

    private static void addBatch(List<Fact> facts, BatchLookup.Snapshot batch) {
        facts.add(Fact.of("volume_planejado", "Volume planejado do lote", batch.volumeLiters(), "L",
                "production"));
        facts.add(Fact.of("volume_envasavel", "Cerveja que existe para envasar",
                batch.packageableVolumeLiters(), "L", "production"));
    }

    /**
     * As métricas calculadas da receita publicada.
     *
     * <p>Vêm do motor de cálculo do módulo de receita, e é justamente isso que o critério da história pede:
     * o número que o modelo vai comentar foi calculado por um serviço de domínio, não por ele. Snapshot sem
     * métrica é caso real — a receita pode ter sido publicada antes do cálculo — e vira fato ausente.
     */
    private void addRecipeMetrics(List<Fact> facts, UUID breweryId, BatchLookup.Snapshot batch) {
        var metrics = recipes.findPublishedForOrder(breweryId, batch.recipeId())
                .flatMap(RecipeLookup.PublishedForOrder::metrics);
        if (metrics.isEmpty()) {
            facts.add(Fact.absent("receita_metricas", "Métricas calculadas da receita", "recipe"));
            return;
        }
        var m = metrics.get();
        facts.add(Fact.of("receita_og", "Densidade inicial prevista (OG)", m.ogSg(), "SG", "recipe"));
        facts.add(Fact.of("receita_fg", "Densidade final prevista (FG)", m.fgSg(), "SG", "recipe"));
        facts.add(Fact.of("receita_abv", "Teor alcoólico previsto", m.abv(), "% v/v", "recipe"));
        facts.add(Fact.of("receita_ibu", "Amargor previsto", m.ibu(), "IBU", "recipe"));
        facts.add(Fact.of("receita_cor", "Cor prevista", m.colorEbc(), "EBC", "recipe"));
    }

    private void addOutcome(List<Fact> facts, UUID breweryId, UUID batchId,
            BatchLookup.Snapshot batch) {
        var outcome = outcomes.outcomeOf(breweryId, batchId);
        if (outcome.isEmpty() || !outcome.get().transferred()) {
            // Ausência, não zero: o lote pode estar fervendo, e "transferiu zero litro" seria mentira.
            facts.add(Fact.absent("volume_transferido", "Volume transferido ao fermentador", "production"));
            facts.add(Fact.absent("perda_transferencia", "Perda na transferência", "production"));
            return;
        }
        var transferred = outcome.get().transferredVolumeLiters();
        var losses = outcome.get().transferLossesLiters();
        facts.add(Fact.of("volume_transferido", "Volume transferido ao fermentador", transferred, "L",
                "production"));
        facts.add(Fact.of("perda_transferencia", "Perda declarada na transferência", losses, "L",
                "production"));

        // Derivado, calculado aqui para que o modelo não precise dividir nada.
        var planned = batch.volumeLiters();
        if (losses != null && planned != null && planned.signum() > 0) {
            facts.add(Fact.of("perda_percentual", "Perda na transferência sobre o volume planejado",
                    losses.multiply(HUNDRED).divide(planned, PERCENT_SCALE, RoundingMode.HALF_UP), "%",
                    "production (derivado)"));
        }
    }

    private void addQuality(List<Fact> facts, UUID breweryId, UUID batchId) {
        var batchQuality = quality.ofBatch(breweryId, batchId);
        if (batchQuality.unmeasured()) {
            // Ninguém mediu não é o mesmo que ficou tudo na faixa; o fato ausente preserva a diferença.
            facts.add(Fact.absent("medicoes", "Medições de qualidade registradas", "quality"));
            return;
        }
        var total = batchQuality.measurements();
        var within = batchQuality.withinSpec();
        facts.add(Fact.count("medicoes", "Medições de qualidade registradas", total, "quality"));
        facts.add(Fact.count("medicoes_na_faixa", "Medições dentro da especificação", within, "quality"));
        facts.add(Fact.count("medicoes_fora_faixa", "Medições fora da especificação",
                batchQuality.outOfSpec().size(), "quality"));
        facts.add(Fact.count("desvios", "Desvios abertos ou registrados",
                batchQuality.deviations().size(), "quality"));
        facts.add(Fact.count("nao_conformidades", "Não conformidades do lote",
                batchQuality.nonConformities().size(), "quality"));

        if (total > 0) {
            facts.add(Fact.of("percentual_na_faixa", "Medições dentro da especificação, em proporção",
                    BigDecimal.valueOf(within).multiply(HUNDRED)
                            .divide(BigDecimal.valueOf(total), PERCENT_SCALE, RoundingMode.HALF_UP),
                    "%", "quality (derivado)"));
        }
    }

    /**
     * Curva, agenda e levedura — os três lugares onde o risco de um lote se manifesta antes do resultado.
     *
     * <p>A distinção entre "sem fermentação registrada" e "registrada e vazia" é preservada de propósito.
     * Um lote que ainda não foi ao fermentador não tem etapa atrasada; um lote com agenda e nenhuma etapa
     * atrasada está em dia. Colapsar os dois em "0 atrasadas" faria o primeiro parecer o segundo.
     */
    private void addFermentation(List<Fact> facts, UUID breweryId, UUID batchId) {
        var estado = fermentation.ofBatch(breweryId, batchId);
        if (estado.isEmpty()) {
            facts.add(Fact.absent("fermentacao", "Fermentação registrada para o lote", "fermentation"));
            return;
        }
        var f = estado.get();
        facts.add(Fact.count("leituras_fermentacao", "Leituras de fermentação registradas",
                f.readingCount(), "fermentation"));

        if (f.lastDensity() == null) {
            facts.add(Fact.absent("densidade_atual", "Última densidade medida", "fermentation"));
        } else {
            facts.add(Fact.of("densidade_atual", "Última densidade medida", f.lastDensity().value(),
                    f.lastDensity().unit(), "fermentation"));
        }
        if (f.lastTemperature() == null) {
            facts.add(Fact.absent("temperatura_atual", "Última temperatura medida", "fermentation"));
        } else {
            facts.add(Fact.of("temperatura_atual", "Última temperatura medida",
                    f.lastTemperature().value(), f.lastTemperature().unit(), "fermentation"));
        }

        if (f.totalSteps() > 0) {
            facts.add(Fact.count("etapas_fermentacao", "Etapas planejadas na agenda", f.totalSteps(),
                    "fermentation"));
            facts.add(Fact.count("etapas_concluidas", "Etapas da agenda já executadas", f.doneSteps(),
                    "fermentation"));
            // O sinal mais direto de lote em apuros, e o que o modelo não teria como inferir dos outros.
            facts.add(Fact.count("etapas_atrasadas", "Etapas pendentes fora da janela planejada",
                    f.lateSteps(), "fermentation"));
        } else {
            facts.add(Fact.absent("etapas_fermentacao", "Etapas planejadas na agenda", "fermentation"));
        }

        if (f.yeastGeneration() == null) {
            // Ausência aqui quer dizer levedura nova, não geração zero — e a diferença muda a leitura.
            facts.add(Fact.absent("geracao_levedura", "Geração da levedura inoculada", "fermentation"));
        } else {
            facts.add(Fact.count("geracao_levedura", "Geração da levedura inoculada",
                    f.yeastGeneration(), "fermentation"));
        }
    }

    private void addCost(List<Fact> facts, UUID breweryId, UUID batchId) {
        var cost = costs.ofBatch(breweryId, batchId);
        if (cost.isEmpty()) {
            facts.add(Fact.absent("custo_total", "Custo apurado do lote", "costing"));
            return;
        }
        var summary = cost.get();
        facts.add(Fact.of("custo_total", "Custo apurado do lote", summary.total(), "", "costing"));
        facts.add(Fact.of("custo_por_litro", "Custo por litro", summary.costPerLiter(), "por L",
                "costing"));
    }

    /**
     * Os fatos de um lote, com o que identifica o lote fora da lista.
     *
     * <p>Código, receita e estado ficam de fora dos fatos numéricos porque não são número — e a conferência
     * varre número. Eles vão ao prompt como contexto, e o modelo pode citá-los sem risco de serem lidos como
     * cálculo inventado.
     */
    public record BatchFacts(String batchCode, String recipeName, int recipeVersion, String status,
            List<Fact> facts) {

        public BatchFacts {
            facts = List.copyOf(facts);
        }
    }
}
