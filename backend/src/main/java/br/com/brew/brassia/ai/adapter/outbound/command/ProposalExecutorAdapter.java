package br.com.brew.brassia.ai.adapter.outbound.command;

import br.com.brew.brassia.ai.application.port.outbound.ProposalExecutor;
import br.com.brew.brassia.ai.domain.CommandProposal;
import br.com.brew.brassia.costing.BatchCostCommands;
import br.com.brew.brassia.sanitation.CleaningCycleCommands;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Onde a proposta confirmada vira comando de verdade (DEB-AIA-002).
 *
 * <p>Antes disto, confirmar gravava o consentimento e levava a pessoa a outra tela para praticar o ato à
 * mão. O consentimento ficava registrado e a execução dependia de alguém não esquecer o segundo passo —
 * numa proposta cuja razão de existir é justamente que "o lote termina, as parcelas entram, e ninguém
 * lembra de fechar".
 *
 * <p><strong>O ator é quem confirmou.</strong> Não quem pediu a análise e não um usuário de sistema: a
 * permissão exigida foi conferida contra ele, e é o nome dele que precisa aparecer na trilha do módulo que
 * executou. Um comando disparado por conta de IA apagaria exatamente o rastro que a confirmação humana
 * existe para criar.
 */
@Component
class ProposalExecutorAdapter implements ProposalExecutor {

    private final BatchCostCommands costs;
    private final CleaningCycleCommands cycles;

    ProposalExecutorAdapter(BatchCostCommands costs, CleaningCycleCommands cycles) {
        this.costs = Objects.requireNonNull(costs, "costs");
        this.cycles = Objects.requireNonNull(cycles, "cycles");
    }

    @Override
    public void execute(CommandProposal proposal, UUID actorId) {
        Objects.requireNonNull(proposal, "proposal");
        Objects.requireNonNull(actorId, "quem confirma é obrigatório");

        var parameters = proposal.parameters();
        switch (proposal.action()) {
            case CLOSE_BATCH_COST -> costs.close(actorId, proposal.breweryId(),
                    UUID.fromString(parameters.get("batchId")),
                    "Fechado a partir de proposta do copiloto: " + proposal.rationale());
            case SCHEDULE_CLEANING_CYCLE -> cycles.start(actorId, proposal.breweryId(),
                    UUID.fromString(parameters.get("equipmentId")), parameters.get("procedureCode"));
            // Continua manual, e o motivo está na própria ação: executá-la exigiria inventar três prazos.
            case OPEN_NON_CONFORMITY -> { }
        }
    }
}
