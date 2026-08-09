package br.com.brew.brassia.ai.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * O prazo da proposta passou (AIA-003).
 *
 * <p>Ela foi feita sobre os fatos de um instante, e aceitá-la agora seria agir sobre um retrato antigo — que
 * é convincente justamente porque parece atual.
 */
public final class ExpiredProposalException extends RuntimeException {

    private final UUID proposalId;
    private final Instant expiresAt;

    public ExpiredProposalException(UUID proposalId, Instant expiresAt) {
        super("esta proposta venceu; peça uma nova com os fatos de agora");
        this.proposalId = proposalId;
        this.expiresAt = expiresAt;
    }

    public UUID proposalId() {
        return proposalId;
    }

    public Instant expiresAt() {
        return expiresAt;
    }
}
