package br.com.brew.brassia.security.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Concessão de acesso temporário: uma permissão pontual, com justificativa e
 * janela de vigência, dada a um usuário numa cervejaria. Concessão de permissão
 * crítica só é efetiva após aprovação de um segundo usuário (≠ solicitante).
 *
 * @param approvedBy aprovador (nulo enquanto pendente)
 * @param revokedAt  instante da revogação (nulo enquanto ativa)
 */
public record TemporaryAccessGrant(
        UUID id,
        UUID breweryId,
        UUID userId,
        UUID permissionId,
        String permissionCode,
        boolean permissionCritical,
        String reason,
        Instant validFrom,
        Instant validUntil,
        UUID requestedBy,
        UUID approvedBy,
        Instant revokedAt) {

    public TemporaryAccessGrant {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(breweryId, "breweryId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(requestedBy, "requestedBy");
        Objects.requireNonNull(validFrom, "validFrom");
        Objects.requireNonNull(validUntil, "validUntil");
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isApproved() {
        return approvedBy != null;
    }

    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(validUntil);
    }

    public boolean isRequestedBy(UUID actorId) {
        return requestedBy.equals(actorId);
    }

    /**
     * Vigora agora? Não revogada, dentro da janela e — se a permissão for
     * crítica — aprovada por um segundo usuário.
     */
    public boolean isEffectiveNow(Instant now) {
        boolean inWindow = !now.isBefore(validFrom) && now.isBefore(validUntil);
        return !isRevoked() && inWindow && (!permissionCritical || isApproved());
    }

    /**
     * O ator pode aprovar esta concessão? Só concessão pendente, não revogada,
     * não expirada e cujo solicitante seja outro (segregação de funções).
     */
    public boolean canApprove(UUID actorId, Instant now) {
        return !isRevoked() && !isApproved() && !isExpiredAt(now) && !isRequestedBy(actorId);
    }

    /** Situação derivada, para a visão administrativa. */
    public Status statusAt(Instant now) {
        if (isRevoked()) {
            return Status.REVOKED;
        }
        if (isExpiredAt(now)) {
            return Status.EXPIRED;
        }
        if (permissionCritical && !isApproved()) {
            return Status.PENDING;
        }
        return Status.ACTIVE;
    }

    public enum Status {
        PENDING,
        ACTIVE,
        EXPIRED,
        REVOKED
    }
}
