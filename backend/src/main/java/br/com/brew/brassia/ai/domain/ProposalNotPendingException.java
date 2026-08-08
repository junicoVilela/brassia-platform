package br.com.brew.brassia.ai.domain;

import java.util.UUID;

/**
 * A proposta já foi decidida (AIA-003).
 *
 * <p>Decisão é definitiva: reabrir apagaria a decisão de alguém. O caminho é propor de novo, com os fatos de
 * agora — que provavelmente não são os mesmos.
 */
public final class ProposalNotPendingException extends RuntimeException {

    private final UUID proposalId;
    private final ProposalStatus status;

    public ProposalNotPendingException(UUID proposalId, ProposalStatus status) {
        super("esta proposta já foi decidida");
        this.proposalId = proposalId;
        this.status = status;
    }

    public UUID proposalId() {
        return proposalId;
    }

    public ProposalStatus status() {
        return status;
    }
}
