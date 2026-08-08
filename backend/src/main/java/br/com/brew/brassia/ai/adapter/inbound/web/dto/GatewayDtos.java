package br.com.brew.brassia.ai.adapter.inbound.web.dto;

import br.com.brew.brassia.ai.application.port.inbound.GatewayCommands.ProbeAnswer;
import br.com.brew.brassia.ai.application.port.inbound.GatewayQueries.GatewayStatus;
import br.com.brew.brassia.ai.domain.AiBudget;
import br.com.brew.brassia.ai.domain.ModelInvocation;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Contratos HTTP do gateway de IA (AIA-001). */
public final class GatewayDtos {

    private GatewayDtos() {
    }

    /**
     * O estado do gateway como a interface precisa lê-lo.
     *
     * <p>{@code enabled} vem separado de qualquer noção de erro porque desligado é estado normal: a UI
     * mostra "sem copiloto nesta instalação", não um alarme.
     */
    public record GatewayStatusView(
            String provider,
            boolean enabled,
            List<String> models,
            long timeoutSeconds,
            BudgetView budget,
            List<InvocationView> recent) {

        public static GatewayStatusView from(GatewayStatus status) {
            return new GatewayStatusView(status.providerName(), status.enabled(), status.models(),
                    status.timeout().toSeconds(), BudgetView.from(status.budget()),
                    status.recent().stream().map(InvocationView::from).toList());
        }
    }

    /**
     * O teto e o consumo do mês.
     *
     * <p>{@code version} viaja para a UI porque ela precisa devolvê-la ao alterar o teto: é assim que a
     * alteração de uma pessoa não sobrescreve, sem aviso, a de outra.
     */
    public record BudgetView(
            BigDecimal monthlyLimit,
            BigDecimal spentThisMonth,
            BigDecimal remaining,
            boolean exhausted,
            String currency,
            long version) {

        public static BudgetView from(AiBudget budget) {
            return new BudgetView(budget.monthlyLimit(), budget.spentThisMonth(), budget.remaining(),
                    budget.exhausted(), budget.currency(), budget.version());
        }
    }

    /** Uma chamada do ledger. Sem prompt e sem resposta — o conteúdo nunca sai daqui. */
    public record InvocationView(
            String purpose,
            String model,
            String status,
            long inputTokens,
            long outputTokens,
            BigDecimal cost,
            String currency,
            long latencyMillis,
            String failureReason,
            Instant occurredAt) {

        public static InvocationView from(ModelInvocation invocation) {
            return new InvocationView(invocation.purpose().name(), invocation.model(),
                    invocation.status().name(), invocation.usage().inputTokens(),
                    invocation.usage().outputTokens(), invocation.cost(), invocation.currency(),
                    invocation.latencyMillis(), invocation.failureReason(), invocation.occurredAt());
        }
    }

    /** O resultado da verificação de conectividade. */
    public record ProbeView(boolean ready, String note) {

        public static ProbeView from(ProbeAnswer answer) {
            return new ProbeView(answer.ready(), answer.note());
        }
    }

    /**
     * Pedido de redefinição do teto.
     *
     * <p>{@code version} é obrigatória e vem da leitura anterior. Zero significa "ainda não havia teto
     * cadastrado" — é o valor que a consulta devolve nesse caso, e mandá-lo de volta é o que distingue
     * primeira definição de alteração.
     */
    public record BudgetRequest(
            @NotNull @DecimalMin("0.00") BigDecimal monthlyLimit,
            @NotNull Long version) {
    }
}
