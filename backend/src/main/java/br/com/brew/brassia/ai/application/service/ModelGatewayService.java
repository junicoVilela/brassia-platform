package br.com.brew.brassia.ai.application.service;

import br.com.brew.brassia.ai.ModelGateway;
import br.com.brew.brassia.ai.application.port.outbound.AiBudgetRepository;
import br.com.brew.brassia.ai.application.port.outbound.ModelInvocationLedger;
import br.com.brew.brassia.ai.application.port.outbound.ModelProvider;
import br.com.brew.brassia.ai.application.port.outbound.StructuredResponseReader;
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
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * O gateway de modelos (AIA-001): orçamento antes, contrato depois, registro sempre.
 *
 * <p>A ordem das verificações é a decisão de desenho:
 *
 * <ol>
 *   <li><strong>Provedor.</strong> Desligado é estado normal e recusa imediata. Nada de objeto vazio
 *       ou texto default fingindo resposta.
 *   <li><strong>Orçamento, antes de gastar.</strong> Conferir depois só serviria para descobrir que
 *       estourou. A estimativa usa o teto de saída — errar para o lado de recusar uma chamada que
 *       caberia é o lado certo de errar quando o dinheiro já saiu.
 *   <li><strong>Chamada, com fallback em ordem.</strong> Provedor que falha cai para o modelo
 *       seguinte; falha do último é indisponibilidade.
 *   <li><strong>Contrato.</strong> Resposta que não satisfaz o tipo pedido é recusada inteira.
 * </ol>
 *
 * <p><strong>Fallback só para falha do provedor, nunca para resposta inválida.</strong> Falha é do
 * outro lado e outro modelo pode responder; resposta fora do contrato é sinal de que o nosso prompt ou
 * o nosso schema está errado, e repetir num segundo modelo gasta dinheiro para colher a mesma classe
 * de erro. Recusar e registrar dá o diagnóstico; insistir dá a conta.
 *
 * <p><strong>O ledger é gravado em qualquer desfecho.</strong> Inclusive na recusa por contrato — a
 * resposta foi gerada, logo foi cobrada. É o que faz o custo do mês corresponder ao gasto real.
 */
public final class ModelGatewayService implements ModelGateway {

    /**
     * Divisor grosseiro de caracteres por token, usado <strong>só</strong> na verificação de orçamento
     * antes da chamada.
     *
     * <p>Contar tokens de verdade exigiria o tokenizador do provedor, que é da borda e varia por
     * modelo. Para decidir "cabe no mês?" uma aproximação basta, porque a decisão já é conservadora
     * pelo teto de saída. O gasto registrado nunca usa esta estimativa: vem do consumo que o provedor
     * informou.
     */
    private static final int CHARS_PER_TOKEN_ESTIMATE = 4;

    private final ModelProvider provider;
    private final AiBudgetRepository budgets;
    private final ModelInvocationLedger ledger;
    private final StructuredResponseReader reader;
    private final AuditTrail audit;
    private final Clock clock;

    public ModelGatewayService(ModelProvider provider, AiBudgetRepository budgets,
            ModelInvocationLedger ledger, StructuredResponseReader reader, AuditTrail audit, Clock clock) {
        this.provider = Objects.requireNonNull(provider);
        this.budgets = Objects.requireNonNull(budgets);
        this.ledger = Objects.requireNonNull(ledger);
        this.reader = Objects.requireNonNull(reader);
        this.audit = Objects.requireNonNull(audit);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public <T> T complete(Prompt prompt, Class<T> contract) {
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(contract, "contract");

        var chain = provider.chain();
        if (!provider.enabled() || chain.isEmpty()) {
            throw refuse(prompt, "-", InvocationStatus.PROVIDER_DISABLED,
                    "nenhum provedor de IA está configurado nesta instalação");
        }

        var budget = budgets.currentOf(prompt.breweryId());
        requireBudget(prompt, chain.getFirst(), budget);

        for (var choice : chain) {
            var startedAt = clock.instant();
            try {
                var completion = provider.send(new ModelProvider.Call(choice.model(), prompt.instruction(),
                        prompt.untrustedInput(), prompt.responseSchema(), prompt.maxOutputTokens()));
                return accept(prompt, choice, completion, startedAt, contract);
            } catch (ModelProvider.ProviderFailure failure) {
                register(prompt, choice, InvocationStatus.PROVIDER_FAILED, failure.usage(), startedAt,
                        failure.getMessage());
            }
        }

        // O motivo de cada tentativa já está no ledger, uma linha por modelo. Repeti-lo aqui daria uma
        // mensagem que fala de um modelo só quando o que importa é que nenhum respondeu.
        throw new AiUnavailableException(InvocationStatus.PROVIDER_FAILED,
                "o provedor de IA não respondeu em nenhum dos modelos configurados");
    }

    /** Aceita a resposta se — e só se — ela satisfizer o contrato. */
    private <T> T accept(Prompt prompt, ModelProvider.ModelChoice choice,
            ModelProvider.Completion completion, Instant startedAt, Class<T> contract) {
        try {
            var answer = reader.read(completion.json(), contract);
            register(prompt, choice, InvocationStatus.SUCCEEDED, completion.usage(), startedAt, null);
            return answer;
        } catch (InvalidModelResponseException rejected) {
            // Gerada e cobrada, ainda que inútil: registrar com o consumo real é o que mantém a
            // conta do mês fiel e expõe o prompt que está produzindo resposta fora de forma.
            register(prompt, choice, InvocationStatus.REJECTED_CONTRACT, completion.usage(), startedAt,
                    rejected.getMessage());
            throw rejected;
        }
    }

    private void requireBudget(Prompt prompt, ModelProvider.ModelChoice choice, AiBudget budget) {
        var estimate = choice.pricing().ceilingCostOf(estimatedInputTokens(prompt), prompt.maxOutputTokens());
        try {
            budget.requireHeadroom(estimate);
        } catch (AiBudgetExceededException exceeded) {
            register(prompt, choice, InvocationStatus.BUDGET_EXCEEDED, TokenUsage.NONE, clock.instant(),
                    "orçamento do mês esgotado");
            throw exceeded;
        }
    }

    private static long estimatedInputTokens(Prompt prompt) {
        var characters = prompt.instruction().length()
                + (prompt.untrustedInput() == null ? 0 : prompt.untrustedInput().length())
                + prompt.responseSchema().length();
        return Math.max(1L, characters / CHARS_PER_TOKEN_ESTIMATE);
    }

    /** Recusa que não chegou a chamar o provedor: registra o motivo e devolve a exceção a lançar. */
    private AiUnavailableException refuse(Prompt prompt, String model, InvocationStatus status,
            String reason) {
        var at = clock.instant();
        var invocation = ModelInvocation.failed(prompt.breweryId(), prompt.actorId(), prompt.purpose(),
                provider.name(), model, status, TokenUsage.NONE, BigDecimal.ZERO, provider.currency(), 0L,
                reason, at);
        ledger.record(invocation);
        auditOf(invocation);
        return new AiUnavailableException(status, reason);
    }

    /**
     * Grava a linha do ledger e o evento de auditoria.
     *
     * <p>Auditoria e ledger respondem perguntas diferentes: o ledger explica a conta, a auditoria
     * explica quem pediu o quê. Nenhum dos dois carrega prompt, resposta ou trecho de documento — o
     * conteúdo é a parte sensível, e nenhuma das duas perguntas precisa dele.
     *
     * <p>A contagem de tokens fica só no ledger, de propósito. O mascarador de auditoria trata qualquer
     * chave que contenha "token" como segredo — e está certo, porque quase sempre é. Como o ledger já
     * guarda a contagem exata e a auditoria só precisa do custo, não há por que disputar a chave.
     */
    private void register(Prompt prompt, ModelProvider.ModelChoice choice, InvocationStatus status,
            TokenUsage usage, Instant startedAt, String failureReason) {
        var finishedAt = clock.instant();
        var latency = Math.max(0L, finishedAt.toEpochMilli() - startedAt.toEpochMilli());
        var pricing = choice.pricing();
        var cost = status.billable() ? pricing.costOf(usage) : BigDecimal.ZERO;

        var invocation = status == InvocationStatus.SUCCEEDED
                ? ModelInvocation.succeeded(prompt.breweryId(), prompt.actorId(), prompt.purpose(),
                        provider.name(), choice.model(), usage, cost, pricing.currency(), latency, finishedAt)
                : ModelInvocation.failed(prompt.breweryId(), prompt.actorId(), prompt.purpose(),
                        provider.name(), choice.model(), status, usage, cost, pricing.currency(), latency,
                        failureReason == null ? status.name() : failureReason, finishedAt);

        ledger.record(invocation);
        auditOf(invocation);
    }

    private void auditOf(ModelInvocation invocation) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("purpose", invocation.purpose().name());
        metadata.put("provider", invocation.provider());
        metadata.put("model", invocation.model());
        metadata.put("status", invocation.status().name());
        metadata.put("cost", invocation.cost().toPlainString());
        metadata.put("currency", invocation.currency());
        metadata.put("latencyMillis", String.valueOf(invocation.latencyMillis()));

        audit.record(new AuditEvent(invocation.occurredAt(), invocation.breweryId(), invocation.actorId(),
                "ai.model.invoke", "ai_model_invocation", invocation.id().toString(),
                invocation.status() == InvocationStatus.SUCCEEDED ? AuditOutcome.SUCCESS : AuditOutcome.FAILURE,
                metadata));
    }
}
