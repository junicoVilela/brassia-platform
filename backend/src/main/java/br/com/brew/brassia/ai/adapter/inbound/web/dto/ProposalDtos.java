package br.com.brew.brassia.ai.adapter.inbound.web.dto;

import br.com.brew.brassia.ai.domain.CommandProposal;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Contratos HTTP das propostas de comando (AIA-003). */
public final class ProposalDtos {

    private ProposalDtos() {
    }

    /**
     * A decisão humana.
     *
     * <p>Só a observação — e nada mais. Nem a ação, nem os parâmetros, nem quem decide: tudo isso já está na
     * proposta gravada e no contexto autenticado. Aceitar ação ou parâmetro no corpo abriria o caminho exato
     * que esta história existe para fechar: confirmar uma coisa e executar outra.
     */
    public record DecisionRequest(@Size(max = 500) String note) {
    }

    /**
     * Uma proposta como a tela precisa ver.
     *
     * <p>{@code requiredPermission} e {@code canConfirm} viajam juntos de propósito: a tela precisa dizer
     * <em>qual</em> alçada falta, não apenas que o botão está desabilitado. E {@code canConfirm} é conveniência
     * de apresentação — a verificação que vale é a do aceite, feita no domínio contra as permissões de então.
     *
     * @param expired estado derivado do prazo, calculado na leitura. Não é coluna: uma proposta não muda de
     *                estado porque o tempo passou, ela só deixa de ser aceitável
     */
    public record ProposalView(
            UUID id,
            String action,
            String label,
            Map<String, String> parameters,
            String rationale,
            String requiredPermission,
            String executionRoute,
            UUID proposedBy,
            Instant proposedAt,
            Instant expiresAt,
            String status,
            boolean expired,
            boolean canConfirm,
            UUID decidedBy,
            Instant decidedAt,
            String decisionNote) {

        public static ProposalView from(CommandProposal proposal, java.util.Set<String> permissions,
                Instant now) {
            var action = proposal.action();
            return new ProposalView(proposal.id(), action.name(), action.label(), proposal.parameters(),
                    proposal.rationale(), action.requiredPermission(), action.executionRoute(),
                    proposal.proposedBy(), proposal.proposedAt(), proposal.expiresAt(),
                    proposal.status().name(), proposal.expiredAt(now),
                    proposal.pending() && !proposal.expiredAt(now)
                            && permissions.contains(action.requiredPermission()),
                    proposal.decidedBy(), proposal.decidedAt(), proposal.decisionNote());
        }

        public static List<ProposalView> from(List<CommandProposal> proposals,
                java.util.Set<String> permissions, Instant now) {
            return proposals.stream().map(p -> from(p, permissions, now)).toList();
        }
    }
}
