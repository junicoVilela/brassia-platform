package br.com.brew.brassia.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.brew.brassia.ai.ModelGateway;
import br.com.brew.brassia.ai.ModelPurpose;
import br.com.brew.brassia.ai.application.service.BatchAssessmentHandler;
import br.com.brew.brassia.ai.application.service.BatchAssessmentHandler.ModelAssessment;
import br.com.brew.brassia.ai.application.service.BatchFactsAssembler;
import br.com.brew.brassia.ai.domain.UnknownBatchException;
import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.costing.BatchCostLookup;
import br.com.brew.brassia.fermentation.FermentationLookup;
import br.com.brew.brassia.production.BatchLookup;
import br.com.brew.brassia.production.BatchOutcomeLookup;
import br.com.brew.brassia.quality.BatchQualityLookup;
import br.com.brew.brassia.recipe.RecipeLookup;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Avaliar um lote (AIA-002).
 *
 * <p>O critério da história — "nenhum número é inventado; cálculos referenciam serviço de domínio" — aparece
 * aqui de dois lados: os fatos que vão ao prompt trazem a origem de cada número, e a afirmação com número
 * fora dos fatos não chega ao usuário.
 */
class BatchAssessmentHandlerTest {

    private static final UUID BREWERY = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();
    private static final UUID BATCH = UUID.randomUUID();
    private static final UUID RECIPE = UUID.randomUUID();

    private final RecordingAudit audit = new RecordingAudit();

    // --- fatos ---

    @Test
    @DisplayName("os fatos vão ao prompt com valor, unidade e o serviço que calculou")
    void fatosLevamOrigem() {
        // É o critério "cálculos referenciam serviço de domínio" tornado verificável: a origem viaja no
        // prompt e depois até a tela.
        var gateway = new SpyGateway(prompt -> usable());
        var handler = handler(gateway, scene());

        var assessment = handler.assess(ACTOR, BREWERY, BATCH);

        var content = gateway.calls.getFirst().untrustedInput();
        assertThat(content).contains("volume_planejado").contains("400").contains("[calculado por: production]");
        assertThat(content).contains("receita_abv").contains("[calculado por: recipe]");
        assertThat(content).contains("perda_percentual").contains("[calculado por: production (derivado)]");
        assertThat(assessment.facts()).anySatisfy(fact -> {
            assertThat(fact.id()).isEqualTo("custo_por_litro");
            assertThat(fact.source()).isEqualTo("costing");
        });
    }

    @Test
    @DisplayName("os fatos da FERMENTAÇÃO chegam ao modelo — curva, agenda e levedura")
    void fatosDeFermentacaoChegam() {
        // DEB-AIA-001: antes disto a avaliação julgava um lote sem ver nada de fermentação, que é onde mora
        // boa parte do risco. Um lote com três etapas atrasadas chegava idêntico a um lote saudável.
        var scene = scene();
        scene.fermentation = Optional.of(new FermentationLookup.Snapshot(842,
                new FermentationLookup.Measurement(new BigDecimal("1.012"), "SG", AGORA_MEDICAO),
                new FermentationLookup.Measurement(new BigDecimal("19.5"), "C", AGORA_MEDICAO),
                2, 5, 3, 5));
        var gateway = new SpyGateway(prompt -> usable());

        var assessment = handler(gateway, scene).assess(ACTOR, BREWERY, BATCH);

        assertThat(assessment.facts()).anySatisfy(fact -> {
            assertThat(fact.id()).isEqualTo("etapas_atrasadas");
            assertThat(fact.value()).isEqualByComparingTo("3");
            assertThat(fact.source()).isEqualTo("fermentation");
        });
        assertThat(assessment.facts()).anySatisfy(fact -> {
            assertThat(fact.id()).isEqualTo("densidade_atual");
            assertThat(fact.value()).isEqualByComparingTo("1.012");
            assertThat(fact.unit()).isEqualTo("SG");
        });
        assertThat(assessment.facts()).anySatisfy(fact -> {
            assertThat(fact.id()).isEqualTo("geracao_levedura");
            assertThat(fact.value()).isEqualByComparingTo("5");
        });
        assertThat(gateway.calls.getFirst().untrustedInput())
                .contains("etapas_atrasadas").contains("[calculado por: fermentation]");
    }

    @Test
    @DisplayName("sem fermentação registrada, o fato é AUSENTE — e não zero etapas atrasadas")
    void semFermentacaoOFatoEAusente() {
        // A distinção é a razão de o retrato ser Optional. "0 atrasadas" num lote que nem foi ao
        // fermentador leria como lote em dia, que é a conclusão oposta da verdadeira.
        var assessment = handler(new SpyGateway(prompt -> usable()), scene())
                .assess(ACTOR, BREWERY, BATCH);

        assertThat(assessment.facts()).anySatisfy(fact -> {
            assertThat(fact.id()).isEqualTo("fermentacao");
            assertThat(fact.value()).isNull();
        });
        assertThat(assessment.facts())
                .noneSatisfy(fact -> assertThat(fact.id()).isEqualTo("etapas_atrasadas"));
    }

    @Test
    @DisplayName("o derivado é calculado pelo domínio, não pedido ao modelo")
    void derivadoVemCalculado() {
        // Sem isto o modelo precisaria dividir 10 por 400 para dizer a perda em porcentagem — e o resultado
        // seria conta de quem não presta contas dela.
        var handler = handler(new SpyGateway(prompt -> usable()), scene());

        var assessment = handler.assess(ACTOR, BREWERY, BATCH);

        assertThat(assessment.facts()).anySatisfy(fact -> {
            assertThat(fact.id()).isEqualTo("perda_percentual");
            // 10 L de perda sobre 400 L planejados = 2,5%.
            assertThat(fact.value()).isEqualByComparingTo("2.5");
            assertThat(fact.source()).contains("derivado");
        });
        assertThat(assessment.facts()).anySatisfy(fact -> {
            assertThat(fact.id()).isEqualTo("percentual_na_faixa");
            // 8 de 10 medições na faixa = 80%.
            assertThat(fact.value()).isEqualByComparingTo("80.0");
        });
    }

    @Test
    @DisplayName("ausência viaja como ausência, nunca como zero")
    void ausenciaNaoViraZero() {
        // Um lote que ainda ferve não transferiu zero litro, e um lote não medido não está aprovado.
        var scene = scene();
        scene.outcome = Optional.empty();
        scene.quality = BatchQualityLookup.BatchQuality.empty();
        var gateway = new SpyGateway(prompt -> usable());
        var handler = handler(gateway, scene);

        var assessment = handler.assess(ACTOR, BREWERY, BATCH);

        assertThat(assessment.facts()).anySatisfy(fact -> {
            assertThat(fact.id()).isEqualTo("volume_transferido");
            assertThat(fact.known()).isFalse();
        });
        assertThat(assessment.facts()).anySatisfy(fact -> {
            assertThat(fact.id()).isEqualTo("medicoes");
            assertThat(fact.known()).isFalse();
        });
        assertThat(gateway.calls.getFirst().untrustedInput()).contains("não disponível");
    }

    @Test
    @DisplayName("receita sem métricas calculadas é ausência declarada, não zero")
    void receitaSemMetricasEhAusencia() {
        // Acontece de verdade: a receita pode ter sido publicada antes de o cálculo rodar.
        var scene = scene();
        scene.metrics = Optional.empty();
        var handler = handler(new SpyGateway(prompt -> usable()), scene);

        var assessment = handler.assess(ACTOR, BREWERY, BATCH);

        assertThat(assessment.facts()).anySatisfy(fact -> {
            assertThat(fact.id()).isEqualTo("receita_metricas");
            assertThat(fact.known()).isFalse();
        });
        assertThat(assessment.facts()).noneSatisfy(fact -> assertThat(fact.id()).isEqualTo("receita_abv"));
    }

    // --- conferência ---

    @Test
    @DisplayName("avaliação que só usa números de fato passa inteira")
    void avaliacaoHonestaPassa() {
        var handler = handler(new SpyGateway(prompt -> usable()), scene());

        var assessment = handler.assess(ACTOR, BREWERY, BATCH);

        assertThat(assessment.usable()).isTrue();
        assertThat(assessment.summary()).contains("390");
        assertThat(assessment.risks()).hasSize(1);
        assertThat(assessment.discarded()).isEmpty();
    }

    @Test
    @DisplayName("risco com número inventado não chega ao usuário")
    void riscoComNumeroInventadoEhDescartado() {
        var handler = handler(new SpyGateway(prompt -> new ModelAssessment(
                summary("O lote transferiu 390 L.", "volume_transferido"),
                List.of(new ModelAssessment.Risk("A eficiência de mosturação foi de 62%.", "HIGH",
                        List.of("volume_planejado"))),
                List.of())), scene());

        var assessment = handler.assess(ACTOR, BREWERY, BATCH);

        // O resumo é honesto e fica; o risco inventou um número e sai.
        assertThat(assessment.usable()).isTrue();
        assertThat(assessment.summary()).contains("390");
        assertThat(assessment.risks()).isEmpty();
        assertThat(assessment.discarded()).singleElement()
                .satisfies(reason -> assertThat(reason).contains("62"));
    }

    @Test
    @DisplayName("resumo com número inventado é descartado, e os riscos honestos ficam")
    void resumoComNumeroInventadoEhDescartado() {
        // 45 existe entre os fatos — é o IBU previsto — mas a afirmação cita a perda, cujo valor é 10.
        // Número certo, assunto errado: recusado.
        var handler = handler(new SpyGateway(prompt -> new ModelAssessment(
                summary("O lote perdeu 45 L na transferência.", "perda_transferencia"),
                List.of(new ModelAssessment.Risk("A perda está acima do usual para o equipamento.",
                        "MEDIUM", List.of("perda_percentual"))),
                List.of())), scene());

        var assessment = handler.assess(ACTOR, BREWERY, BATCH);

        assertThat(assessment.summary()).isEmpty();
        assertThat(assessment.risks()).hasSize(1);
        // Ainda é utilizável: sobrou análise sustentada.
        assertThat(assessment.usable()).isTrue();
        assertThat(assessment.discarded()).hasSize(1);
    }

    @Test
    @DisplayName("nada resistindo à conferência deixa a avaliação inutilizável")
    void nadaResistindoEhInutilizavel() {
        var handler = handler(new SpyGateway(prompt -> new ModelAssessment(
                summary("A eficiência global foi de 71%.", "volume_planejado"),
                List.of(new ModelAssessment.Risk("O rendimento caiu 18% em relação ao alvo.", "HIGH",
                        List.of("volume_planejado"))),
                List.of())), scene());

        var assessment = handler.assess(ACTOR, BREWERY, BATCH);

        assertThat(assessment.usable()).isFalse();
        assertThat(assessment.summary()).isEmpty();
        assertThat(assessment.risks()).isEmpty();
        assertThat(assessment.discarded()).hasSize(2);
        // Os fatos continuam: eles são do domínio e valem independentemente do que o modelo disse.
        assertThat(assessment.facts()).isNotEmpty();
    }

    @Test
    @DisplayName("suposição com número inventado também é descartada")
    void suposicaoComNumeroEhDescartada() {
        // Suposição não declara fato, e por isso não pode conter número nenhum — o que é o certo: uma
        // suposição com número é o número inventado no seu disfarce mais convincente.
        var handler = handler(new SpyGateway(prompt -> new ModelAssessment(
                summary("O lote transferiu 390 L.", "volume_transferido"), List.of(),
                List.of("Supondo eficiência de 75%, o extrato seria menor.",
                        "Supondo que a temperatura ambiente estava dentro do usual."))), scene());

        var assessment = handler.assess(ACTOR, BREWERY, BATCH);

        assertThat(assessment.assumptions()).singleElement()
                .satisfies(assumption -> assertThat(assumption).contains("temperatura ambiente"));
        assertThat(assessment.discarded()).hasSize(1);
    }

    // --- contrato e contexto ---

    @Test
    @DisplayName("o schema não tem campo de ação: propor comando é AIA-003")
    void schemaNaoTemAcao() {
        var gateway = new SpyGateway(prompt -> usable());
        handler(gateway, scene()).assess(ACTOR, BREWERY, BATCH);

        var prompt = gateway.calls.getFirst();
        assertThat(prompt.purpose()).isEqualTo(ModelPurpose.BATCH_ASSESSMENT);
        assertThat(prompt.responseSchema()).doesNotContain("command").doesNotContain("action");
        assertThat(prompt.responseSchema()).contains("\"additionalProperties\": false");
        assertThat(prompt.instruction()).contains("NÃO ESCREVA NENHUM NÚMERO");
        assertThat(prompt.instruction()).contains("NÃO PODE conter número");
    }

    @Test
    @DisplayName("lote que não existe nesta cervejaria não é avaliado")
    void loteInexistenteNaoEhAvaliado() {
        var scene = scene();
        scene.batch = Optional.empty();
        var gateway = new SpyGateway(prompt -> usable());

        assertThatThrownBy(() -> handler(gateway, scene).assess(ACTOR, BREWERY, BATCH))
                .isInstanceOf(UnknownBatchException.class);
        // E o modelo não é chamado: não há o que avaliar, e a chamada custaria.
        assertThat(gateway.calls).isEmpty();
    }

    @Test
    @DisplayName("avaliação sem autor não existe")
    void autorEhObrigatorio() {
        assertThatThrownBy(() -> handler(new SpyGateway(prompt -> usable()), scene())
                .assess(null, BREWERY, BATCH))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("a auditoria registra as contagens e o desfecho, nunca o texto da avaliação")
    void auditoriaNaoCarregaTexto() {
        var secret = "cliente Zé do bar da esquina";
        var handler = handler(new SpyGateway(prompt -> new ModelAssessment(
                summary("O lote transferiu 390 L para o " + secret + ".", "volume_transferido"),
                List.of(), List.of())), scene());

        handler.assess(ACTOR, BREWERY, BATCH);

        assertThat(audit.events).singleElement().satisfies(event -> {
            assertThat(event.action()).isEqualTo("ai.assessment.batch");
            assertThat(event.resourceId()).isEqualTo(BATCH.toString());
            assertThat(event.metadata()).containsKey("facts").containsKey("discardedClaims");
            assertThat(event.metadata().values())
                    .noneSatisfy(value -> assertThat(value).contains(secret));
        });
    }

    // --- infraestrutura do teste ---

    /** Cenário mutável: cada teste ajusta a peça que lhe interessa. */
    private static final Instant AGORA_MEDICAO = Instant.parse("2026-08-08T06:00:00Z");

    private static final class Scene {
        /**
         * Fermentação vazia por padrão: a maioria dos casos aqui é sobre a conferência do texto do modelo,
         * não sobre a curva. Os testes que se importam com ela a preenchem.
         */
        Optional<FermentationLookup.Snapshot> fermentation = Optional.empty();
        Optional<BatchLookup.Snapshot> batch = Optional.of(new BatchLookup.Snapshot(BATCH,
                UUID.randomUUID(), "LOTE-100", new BigDecimal("400"), new BigDecimal("390"),
                "FERMENTING", RECIPE, 2, "IPA da casa"));
        Optional<BatchOutcomeLookup.BatchOutcome> outcome = Optional.of(
                new BatchOutcomeLookup.BatchOutcome(new BigDecimal("400"), new BigDecimal("390"),
                        new BigDecimal("10")));
        BatchQualityLookup.BatchQuality quality = new BatchQualityLookup.BatchQuality(10, 8,
                List.of(), List.of(), List.of());
        Optional<BatchCostLookup.CostSummary> cost = Optional.of(new BatchCostLookup.CostSummary(
                new BigDecimal("780.00"), new BigDecimal("2.0000"), new BigDecimal("390"), false,
                true, List.of("sem mão de obra")));
        Optional<RecipeLookup.Metrics> metrics = Optional.of(new RecipeLookup.Metrics(
                new BigDecimal("1.060"), new BigDecimal("1.012"), new BigDecimal("6.3"),
                new BigDecimal("45"), new BigDecimal("22")));
    }

    private static Scene scene() {
        return new Scene();
    }

    private BatchAssessmentHandler handler(SpyGateway gateway, Scene scene) {
        var assembler = new BatchFactsAssembler(
                (breweryId, batchId) -> scene.batch,
                (breweryId, batchId) -> scene.outcome,
                (breweryId, batchId) -> scene.quality,
                (breweryId, batchId) -> scene.cost,
                new RecipeLookup() {
                    @Override
                    public Optional<ExpectedLoss> expectedLoss(UUID breweryId, UUID recipeId) {
                        return Optional.empty();
                    }

                    @Override
                    public Optional<PublishedRecipe> findPublished(UUID breweryId, UUID recipeId) {
                        return Optional.empty();
                    }

                    @Override
                    public Optional<PublishedComposition> findPublishedComposition(UUID breweryId,
                            UUID recipeId) {
                        return Optional.empty();
                    }

                    @Override
                    public Optional<PublishedForOrder> findPublishedForOrder(UUID breweryId,
                            UUID recipeId) {
                        return Optional.of(new PublishedForOrder(recipeId, 2, "IPA da casa", null,
                                new BigDecimal("400"), scene.metrics));
                    }
                },
                (breweryId, batchId) -> scene.fermentation);
        return new BatchAssessmentHandler(assembler, gateway, audit,
                Clock.fixed(Instant.parse("2026-08-08T12:00:00Z"), ZoneOffset.UTC));
    }

    private static ModelAssessment usable() {
        return new ModelAssessment(summary("O lote transferiu 390 L dos 400 L planejados.",
                        "volume_transferido", "volume_planejado"),
                List.of(new ModelAssessment.Risk("A perda de 2.5% merece acompanhamento.", "LOW",
                        List.of("perda_percentual"))),
                List.of());
    }

    private static ModelAssessment.Summary summary(String text, String... refs) {
        return new ModelAssessment.Summary(text, List.of(refs));
    }

    private static final class SpyGateway implements ModelGateway {

        private final List<Prompt> calls = new ArrayList<>();
        private final Function<Prompt, ModelAssessment> behaviour;

        SpyGateway(Function<Prompt, ModelAssessment> behaviour) {
            this.behaviour = behaviour;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T complete(Prompt prompt, Class<T> contract) {
            calls.add(prompt);
            return (T) behaviour.apply(prompt);
        }
    }

    private static final class RecordingAudit implements AuditTrail {

        private final List<AuditEvent> events = new ArrayList<>();

        @Override
        public void record(AuditEvent event) {
            events.add(event);
        }
    }
}
