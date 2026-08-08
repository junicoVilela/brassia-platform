package br.com.brew.brassia.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.brew.brassia.ai.ModelGateway;
import br.com.brew.brassia.ai.ModelPurpose;
import br.com.brew.brassia.ai.application.port.outbound.AiBudgetRepository;
import br.com.brew.brassia.ai.application.port.outbound.ModelInvocationLedger;
import br.com.brew.brassia.ai.application.port.outbound.ModelProvider;
import br.com.brew.brassia.ai.application.port.outbound.StructuredResponseReader;
import br.com.brew.brassia.ai.application.service.ModelGatewayService;
import br.com.brew.brassia.ai.domain.AiBudget;
import br.com.brew.brassia.ai.domain.AiBudgetExceededException;
import br.com.brew.brassia.ai.domain.AiUnavailableException;
import br.com.brew.brassia.ai.domain.InvalidModelResponseException;
import br.com.brew.brassia.ai.domain.InvocationStatus;
import br.com.brew.brassia.ai.domain.ModelInvocation;
import br.com.brew.brassia.ai.domain.ModelPricing;
import br.com.brew.brassia.ai.domain.TokenUsage;
import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditOutcome;
import br.com.brew.brassia.audit.AuditTrail;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * O gateway de modelos (AIA-001).
 *
 * <p>O que estes testes fixam é a ordem das defesas e o que sobra registrado em cada desfecho. Os quatro
 * riscos da sprint aparecem aqui: provedor indisponível não quebra fluxo, resposta inválida é recusada
 * inteira, orçamento estourado impede a chamada, e o conteúdo do prompt não escapa para registro nenhum.
 */
class ModelGatewayServiceTest {

    private static final UUID BREWERY = UUID.randomUUID();
    private static final UUID OTHER_BREWERY = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();

    private static final ModelPricing PRICING = new ModelPricing(
            new BigDecimal("5.00"), new BigDecimal("25.00"), "USD");

    /** Um segredo plausível de prompt real: se aparecer em registro, o teste falha. */
    private static final String SENSITIVE = "peracético 0,15% no tanque T-3";

    private final InMemoryLedger ledger = new InMemoryLedger();
    private final RecordingAudit audit = new RecordingAudit();

    // --- provedor desligado ---

    @Test
    @DisplayName("provedor desligado recusa sem quebrar, e a recusa fica registrada")
    void provedorDesligadoRecusaExplicitamente() {
        var gateway = gateway(new FakeProvider(false, List.of()), budget("100.00", "0.00"), json -> answer());

        assertThatThrownBy(() -> gateway.complete(prompt(BREWERY), Answer.class))
                .isInstanceOf(AiUnavailableException.class)
                .satisfies(thrown -> assertThat(((AiUnavailableException) thrown).disabled()).isTrue());

        // Registrado mesmo sem gasto: é assim que quem opera descobre que alguém tentou usar IA numa
        // instalação que não tem IA, em vez de ficar com um mistério na interface.
        assertThat(ledger.lines).singleElement().satisfies(line -> {
            assertThat(line.status()).isEqualTo(InvocationStatus.PROVIDER_DISABLED);
            assertThat(line.cost()).isEqualByComparingTo("0");
            assertThat(line.usage()).isEqualTo(TokenUsage.NONE);
        });
    }

    @Test
    @DisplayName("cadeia de modelos vazia é tratada como provedor desligado")
    void cadeiaVaziaEhDesligado() {
        // Habilitado sem modelo é configuração pela metade; o gateway não pode tentar chamar "nada".
        var gateway = gateway(new FakeProvider(true, List.of()), budget("100.00", "0.00"), json -> answer());

        assertThatThrownBy(() -> gateway.complete(prompt(BREWERY), Answer.class))
                .isInstanceOf(AiUnavailableException.class);
        assertThat(ledger.lines).singleElement()
                .satisfies(line -> assertThat(line.status()).isEqualTo(InvocationStatus.PROVIDER_DISABLED));
    }

    // --- caminho bem-sucedido ---

    @Test
    @DisplayName("resposta válida vira objeto, com custo e latência registrados")
    void respostaValidaEhAceita() {
        var provider = new FakeProvider(true, List.of(model("primary")));
        provider.respondWith("primary", new ModelProvider.Completion("{}", new TokenUsage(1_000, 200)));
        var gateway = gateway(provider, budget("100.00", "0.00"), json -> answer());

        var result = gateway.complete(prompt(BREWERY), Answer.class);

        assertThat(result).isNotNull();
        assertThat(ledger.lines).singleElement().satisfies(line -> {
            assertThat(line.status()).isEqualTo(InvocationStatus.SUCCEEDED);
            assertThat(line.model()).isEqualTo("primary");
            // 1.000 entrada a 5/M + 200 saída a 25/M = 0,005 + 0,005 = 0,01.
            assertThat(line.cost()).isEqualByComparingTo("0.010000");
            assertThat(line.failureReason()).isNull();
        });
        assertThat(audit.events).singleElement().satisfies(event -> {
            assertThat(event.action()).isEqualTo("ai.model.invoke");
            assertThat(event.outcome()).isEqualTo(AuditOutcome.SUCCESS);
            assertThat(event.breweryId()).isEqualTo(BREWERY);
            assertThat(event.actorId()).isEqualTo(ACTOR);
        });
    }

    @Test
    @DisplayName("o instrutor confiável e o conteúdo não confiável chegam separados ao provedor")
    void instrucaoEConteudoNaoSeMisturam() {
        // A separação é o que vai permitir, na RAG-002, tratar documento recuperado como suspeito. Se o
        // gateway concatenasse os dois num texto só, essa distinção morreria aqui e não haveria como
        // recuperá-la depois.
        var provider = new FakeProvider(true, List.of(model("primary")));
        provider.respondWith("primary", new ModelProvider.Completion("{}", new TokenUsage(10, 10)));
        var gateway = gateway(provider, budget("100.00", "0.00"), json -> answer());

        gateway.complete(prompt(BREWERY), Answer.class);

        assertThat(provider.calls).singleElement().satisfies(call -> {
            assertThat(call.instruction()).doesNotContain(SENSITIVE);
            assertThat(call.untrustedInput()).contains(SENSITIVE);
        });
    }

    // --- resposta fora do contrato ---

    @Test
    @DisplayName("resposta inválida é recusada inteira, e o custo dela é registrado")
    void respostaInvalidaEhRecusadaMasCobrada() {
        var provider = new FakeProvider(true, List.of(model("primary")));
        provider.respondWith("primary", new ModelProvider.Completion("{}", new TokenUsage(1_000, 200)));
        var gateway = gateway(provider, budget("100.00", "0.00"), json -> {
            throw new InvalidModelResponseException("faltou campo");
        });

        assertThatThrownBy(() -> gateway.complete(prompt(BREWERY), Answer.class))
                .isInstanceOf(InvalidModelResponseException.class);

        assertThat(ledger.lines).singleElement().satisfies(line -> {
            assertThat(line.status()).isEqualTo(InvocationStatus.REJECTED_CONTRACT);
            // Pagou e não serviu: é justamente essa combinação que precisa aparecer na conta.
            assertThat(line.cost()).isEqualByComparingTo("0.010000");
            assertThat(line.usage().outputTokens()).isEqualTo(200);
        });
    }

    @Test
    @DisplayName("resposta inválida não cai para o fallback: repetir gastaria para colher o mesmo erro")
    void respostaInvalidaNaoTentaOutroModelo() {
        var provider = new FakeProvider(true, List.of(model("primary"), model("fallback")));
        provider.respondWith("primary", new ModelProvider.Completion("{}", new TokenUsage(100, 100)));
        provider.respondWith("fallback", new ModelProvider.Completion("{}", new TokenUsage(100, 100)));
        var gateway = gateway(provider, budget("100.00", "0.00"), json -> {
            throw new InvalidModelResponseException("faltou campo");
        });

        assertThatThrownBy(() -> gateway.complete(prompt(BREWERY), Answer.class))
                .isInstanceOf(InvalidModelResponseException.class);

        assertThat(provider.calls).hasSize(1);
        assertThat(ledger.lines).hasSize(1);
    }

    // --- fallback ---

    @Test
    @DisplayName("falha do provedor cai para o modelo seguinte, e as duas tentativas ficam registradas")
    void falhaCaiParaOFallback() {
        var provider = new FakeProvider(true, List.of(model("primary"), model("fallback")));
        provider.failWith("primary", new ModelProvider.ProviderFailure("indisponível"));
        provider.respondWith("fallback", new ModelProvider.Completion("{}", new TokenUsage(100, 100)));
        var gateway = gateway(provider, budget("100.00", "0.00"), json -> answer());

        var result = gateway.complete(prompt(BREWERY), Answer.class);

        assertThat(result).isNotNull();
        assertThat(provider.calls).hasSize(2);
        // Duas linhas: a tentativa que falhou não desaparece porque a seguinte deu certo. Sem ela ninguém
        // veria que o modelo preferido está fora do ar.
        assertThat(ledger.lines).hasSize(2);
        assertThat(ledger.lines.get(0).status()).isEqualTo(InvocationStatus.PROVIDER_FAILED);
        assertThat(ledger.lines.get(0).model()).isEqualTo("primary");
        assertThat(ledger.lines.get(1).status()).isEqualTo(InvocationStatus.SUCCEEDED);
        assertThat(ledger.lines.get(1).model()).isEqualTo("fallback");
    }

    @Test
    @DisplayName("falha em todos os modelos é indisponibilidade, não resposta vazia")
    void falhaEmTodosEhIndisponibilidade() {
        var provider = new FakeProvider(true, List.of(model("primary"), model("fallback")));
        provider.failWith("primary", new ModelProvider.ProviderFailure("indisponível"));
        provider.failWith("fallback", new ModelProvider.ProviderFailure("indisponível"));
        var gateway = gateway(provider, budget("100.00", "0.00"), json -> answer());

        assertThatThrownBy(() -> gateway.complete(prompt(BREWERY), Answer.class))
                .isInstanceOf(AiUnavailableException.class)
                .satisfies(thrown -> assertThat(((AiUnavailableException) thrown).disabled()).isFalse());

        assertThat(ledger.lines).hasSize(2)
                .allSatisfy(line -> assertThat(line.status()).isEqualTo(InvocationStatus.PROVIDER_FAILED));
    }

    @Test
    @DisplayName("falha depois de gerar é cobrada: gerou, custou")
    void falhaComConsumoEhCobrada() {
        var provider = new FakeProvider(true, List.of(model("primary")));
        provider.failWith("primary",
                new ModelProvider.ProviderFailure("truncada", new TokenUsage(1_000, 200), null));
        var gateway = gateway(provider, budget("100.00", "0.00"), json -> answer());

        assertThatThrownBy(() -> gateway.complete(prompt(BREWERY), Answer.class))
                .isInstanceOf(AiUnavailableException.class);

        assertThat(ledger.lines).singleElement()
                .satisfies(line -> assertThat(line.cost()).isEqualByComparingTo("0.010000"));
    }

    // --- orçamento ---

    @Test
    @DisplayName("orçamento estourado impede a chamada: o provedor nem é tocado")
    void orcamentoEstouradoImpedeAChamada() {
        var provider = new FakeProvider(true, List.of(model("primary")));
        provider.respondWith("primary", new ModelProvider.Completion("{}", new TokenUsage(10, 10)));
        var gateway = gateway(provider, budget("1.00", "1.00"), json -> answer());

        assertThatThrownBy(() -> gateway.complete(prompt(BREWERY), Answer.class))
                .isInstanceOf(AiBudgetExceededException.class);

        // O ponto: verificar depois de chamar seria descobrir que estourou com o dinheiro já gasto.
        assertThat(provider.calls).isEmpty();
        assertThat(ledger.lines).singleElement().satisfies(line -> {
            assertThat(line.status()).isEqualTo(InvocationStatus.BUDGET_EXCEEDED);
            assertThat(line.cost()).isEqualByComparingTo("0");
        });
    }

    @Test
    @DisplayName("a verificação é sobre o pior caso, não sobre a resposta provável")
    void verificacaoUsaOTetoDeSaida() {
        var provider = new FakeProvider(true, List.of(model("primary")));
        provider.respondWith("primary", new ModelProvider.Completion("{}", new TokenUsage(10, 10)));
        // Teto de 4.000 tokens de saída a 25/M = 0,10. Sobra 0,05: não cabe, ainda que a resposta real
        // fosse custar centavos.
        var gateway = gateway(provider, budget("10.00", "9.95"), json -> answer());

        assertThatThrownBy(() -> gateway.complete(promptWithCeiling(BREWERY, 4_000), Answer.class))
                .isInstanceOf(AiBudgetExceededException.class);
        assertThat(provider.calls).isEmpty();
    }

    // --- isolamento entre cervejarias ---

    @Test
    @DisplayName("cada cervejaria consulta o seu próprio orçamento e escreve no seu próprio ledger")
    void isolaPorCervejaria() {
        var provider = new FakeProvider(true, List.of(model("primary")));
        provider.respondWith("primary", new ModelProvider.Completion("{}", new TokenUsage(100, 100)));
        var budgets = new FakeBudgets();
        // Uma esgotada, a outra com folga: o teto de uma cervejaria não pode barrar nem liberar a outra.
        budgets.put(BREWERY, budget("1.00", "1.00"));
        budgets.put(OTHER_BREWERY, budget("100.00", "0.00"));
        var gateway = new ModelGatewayService(provider, budgets, ledger, reader(json -> answer()), audit,
                fixedClock());

        assertThatThrownBy(() -> gateway.complete(prompt(BREWERY), Answer.class))
                .isInstanceOf(AiBudgetExceededException.class);
        assertThat(gateway.complete(prompt(OTHER_BREWERY), Answer.class)).isNotNull();

        assertThat(ledger.lines).hasSize(2);
        assertThat(ledger.linesOf(BREWERY)).singleElement()
                .satisfies(line -> assertThat(line.status()).isEqualTo(InvocationStatus.BUDGET_EXCEEDED));
        assertThat(ledger.linesOf(OTHER_BREWERY)).singleElement()
                .satisfies(line -> assertThat(line.status()).isEqualTo(InvocationStatus.SUCCEEDED));
    }

    // --- repetição ---

    @Test
    @DisplayName("repetir a mesma chamada acumula linhas: cada chamada é um gasto próprio")
    void repeticaoAcumula() {
        // Não há idempotência a inventar aqui: duas perguntas iguais custam duas vezes de verdade, e um
        // ledger que colapsasse as duas numa linha esconderia metade da conta.
        var provider = new FakeProvider(true, List.of(model("primary")));
        provider.respondWith("primary", new ModelProvider.Completion("{}", new TokenUsage(100, 100)));
        var gateway = gateway(provider, budget("100.00", "0.00"), json -> answer());

        gateway.complete(prompt(BREWERY), Answer.class);
        gateway.complete(prompt(BREWERY), Answer.class);

        assertThat(ledger.lines).hasSize(2);
        assertThat(ledger.lines.get(0).id()).isNotEqualTo(ledger.lines.get(1).id());
    }

    // --- sigilo ---

    @Test
    @DisplayName("nem ledger nem auditoria carregam prompt, conteúdo ou resposta")
    void registroNaoCarregaConteudo() {
        var provider = new FakeProvider(true, List.of(model("primary")));
        provider.respondWith("primary",
                new ModelProvider.Completion("{\"leak\":\"" + SENSITIVE + "\"}", new TokenUsage(100, 100)));
        var gateway = gateway(provider, budget("100.00", "0.00"), json -> answer());

        gateway.complete(prompt(BREWERY), Answer.class);

        var line = ledger.lines.getFirst();
        assertThat(line.failureReason()).isNull();
        assertThat(line.model()).doesNotContain(SENSITIVE);
        assertThat(audit.events.getFirst().metadata().values())
                .noneSatisfy(value -> assertThat(value).contains(SENSITIVE));
    }

    // --- infraestrutura do teste ---

    private ModelGateway gateway(FakeProvider provider, AiBudget budget,
            Function<String, Answer> readerBehaviour) {
        var budgets = new FakeBudgets();
        budgets.put(BREWERY, budget);
        budgets.put(OTHER_BREWERY, budget);
        return new ModelGatewayService(provider, budgets, ledger, reader(readerBehaviour), audit,
                fixedClock());
    }

    private static Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-08-07T12:00:00Z"), ZoneOffset.UTC);
    }

    private static StructuredResponseReader reader(Function<String, Answer> behaviour) {
        return new StructuredResponseReader() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> T read(String json, Class<T> contract) {
                return (T) behaviour.apply(json);
            }
        };
    }

    private static ModelGateway.Prompt prompt(UUID breweryId) {
        return promptWithCeiling(breweryId, 256);
    }

    private static ModelGateway.Prompt promptWithCeiling(UUID breweryId, int maxOutputTokens) {
        return new ModelGateway.Prompt(breweryId, ACTOR, ModelPurpose.CONNECTIVITY_PROBE,
                "Você é o verificador.", "Considere: " + SENSITIVE, "{\"type\":\"object\"}",
                maxOutputTokens);
    }

    private static ModelProvider.ModelChoice model(String name) {
        return new ModelProvider.ModelChoice(name, PRICING);
    }

    private static AiBudget budget(String limit, String spent) {
        return AiBudget.defaultOf(BREWERY, new BigDecimal(limit), "USD", new BigDecimal(spent));
    }

    private static Answer answer() {
        return new Answer(true);
    }

    record Answer(boolean ok) {}

    /** Provedor de mentira com respostas e falhas programadas por modelo. */
    private static final class FakeProvider implements ModelProvider {

        private final boolean enabled;
        private final List<ModelChoice> chain;
        private final List<Call> calls = new ArrayList<>();
        private final java.util.Map<String, Completion> answers = new java.util.HashMap<>();
        private final java.util.Map<String, ProviderFailure> failures = new java.util.HashMap<>();

        FakeProvider(boolean enabled, List<ModelChoice> chain) {
            this.enabled = enabled;
            this.chain = chain;
        }

        void respondWith(String model, Completion completion) {
            answers.put(model, completion);
        }

        void failWith(String model, ProviderFailure failure) {
            failures.put(model, failure);
        }

        @Override public boolean enabled() { return enabled; }
        @Override public String name() { return "fake"; }
        @Override public List<ModelChoice> chain() { return chain; }
        @Override public Duration timeout() { return Duration.ofSeconds(5); }
        @Override public String currency() { return "USD"; }

        @Override
        public Completion send(Call call) {
            calls.add(call);
            var failure = failures.get(call.model());
            if (failure != null) {
                throw failure;
            }
            var answer = answers.get(call.model());
            if (answer == null) {
                throw new IllegalStateException("teste não programou resposta para " + call.model());
            }
            return answer;
        }
    }

    private static final class InMemoryLedger implements ModelInvocationLedger {

        private final List<ModelInvocation> lines = new ArrayList<>();

        @Override
        public void record(ModelInvocation invocation) {
            lines.add(invocation);
        }

        @Override
        public BigDecimal spentSince(UUID breweryId, Instant since) {
            return linesOf(breweryId).stream()
                    .map(ModelInvocation::cost)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        @Override
        public List<ModelInvocation> recent(UUID breweryId, int limit) {
            return linesOf(breweryId);
        }

        List<ModelInvocation> linesOf(UUID breweryId) {
            return lines.stream().filter(line -> line.breweryId().equals(breweryId)).toList();
        }
    }

    private static final class FakeBudgets implements AiBudgetRepository {

        private final java.util.Map<UUID, AiBudget> byBrewery = new java.util.HashMap<>();

        void put(UUID breweryId, AiBudget budget) {
            byBrewery.put(breweryId, budget);
        }

        @Override
        public AiBudget currentOf(UUID breweryId) {
            var budget = byBrewery.get(breweryId);
            if (budget == null) {
                throw new IllegalStateException("teste não programou orçamento para " + breweryId);
            }
            return budget;
        }

        @Override
        public AiBudget save(AiBudget budget, long expectedVersion) {
            byBrewery.put(budget.breweryId(), budget);
            return budget;
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
