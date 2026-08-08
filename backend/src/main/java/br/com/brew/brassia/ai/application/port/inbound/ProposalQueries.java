package br.com.brew.brassia.ai.application.port.inbound;

import br.com.brew.brassia.ai.domain.CommandProposal;
import java.util.List;
import java.util.UUID;

/**
 * Consultar propostas e as decisões tomadas sobre elas (AIA-003).
 *
 * <p>Separada dos comandos porque a alçada é outra: {@code ai.command.read} deixa ver o que foi proposto e
 * quem decidiu — auditoria — sem deixar propor nem confirmar nada.
 */
public interface ProposalQueries {

    /** Propostas da cervejaria, das mais recentes para as mais antigas. */
    List<CommandProposal> list(UUID breweryId);
}
