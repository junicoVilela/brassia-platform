package br.com.brew.brassia.ai.domain;

import java.util.UUID;

/** A proposta não existe nesta cervejaria (AIA-003). */
public final class UnknownProposalException extends RuntimeException {

    private final UUID proposalId;

    public UnknownProposalException(UUID proposalId) {
        super("esta proposta não existe nesta cervejaria");
        this.proposalId = proposalId;
    }

    public UUID proposalId() {
        return proposalId;
    }
}
