package br.com.brew.brassia.ai.application.port.outbound;

import br.com.brew.brassia.ai.domain.CommandProposal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Propostas de comando (AIA-003). */
public interface CommandProposalRepository {

    void insert(CommandProposal proposal);

    /**
     * Grava a decisão.
     *
     * <p>Condicionada a a proposta ainda estar pendente: dois cliques simultâneos em "confirmar" não podem
     * produzir dois aceites da mesma proposta, e o segundo precisa descobrir isso.
     *
     * @return falso quando alguém decidiu primeiro
     */
    boolean saveDecision(CommandProposal proposal);

    Optional<CommandProposal> find(UUID breweryId, UUID proposalId);

    /** Propostas da cervejaria, das mais recentes para as mais antigas. */
    List<CommandProposal> findAll(UUID breweryId);
}
