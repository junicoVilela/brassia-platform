package br.com.brew.brassia.security.domain;

import br.com.brew.brassia.shared.security.ForbiddenException;
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

    /**
     * Valida segregação de funções e estado pendente; devolve a concessão aprovada.
     *
     * @throws ForbiddenException se o solicitante tenta aprovar a própria concessão
     * @throws IllegalStateException se a concessão não está pendente
     */
    public TemporaryAccessGrant approve(UUID actorId, Instant now) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(now, "now");
        if (isRequestedBy(actorId)) {
            throw new ForbiddenException("aprovador não pode ser o solicitante");
        }
        if (isRevoked() || isExpiredAt(now) || isApproved()) {
            throw new IllegalStateException("concessão não está pendente");
        }
        return new TemporaryAccessGrant(
                id, breweryId, userId, permissionId, permissionCode, permissionCritical, reason,
                validFrom, validUntil, requestedBy, actorId, revokedAt);
    }

    /**
     * @throws IllegalStateException se já revogada
     */
    public TemporaryAccessGrant revoke(Instant now) {
        Objects.requireNonNull(now, "now");
        if (isRevoked()) {
            throw new IllegalStateException("concessão já revogada");
        }
        return new TemporaryAccessGrant(
                id, breweryId, userId, permissionId, permissionCode, permissionCritical, reason,
                validFrom, validUntil, requestedBy, approvedBy, now);
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
