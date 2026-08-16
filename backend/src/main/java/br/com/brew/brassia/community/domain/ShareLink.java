package br.com.brew.brassia.community.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Um link revogável para uma publicação (COM-002).
 *
 * <p><strong>Só o hash do token é guardado</strong>, como no token de conta e no segredo do webhook. O
 * valor legível aparece uma vez, na criação, e nunca mais: um link vazado do banco seria acesso concedido
 * sem que ninguém tivesse compartilhado nada.
 *
 * <p><strong>O link não eleva visibilidade — e essa é a regra que o critério da história exige</strong>
 * ("acesso nunca ignora autorização ou visibilidade"). Ele não transforma privado em legível: ele é o que
 * torna alcançável o que já está em {@link Visibility#LINK}. Se o autor fechar a publicação para
 * {@code PRIVATE}, todo link existente para de funcionar no mesmo instante, sem precisar revogar um por
 * um — porque a decisão de quem vê é da publicação, e o link só carrega a chave.
 *
 * <p><strong>Revogar e expirar são coisas diferentes, e as duas existem.</strong> Expirar é o prazo
 * combinado; revogar é o arrependimento. Um link sem prazo é legítimo — "manda para o pessoal ver" — e
 * por isso a validade é opcional; o que não é opcional é poder cortar.
 */
public final class ShareLink {

    private static final int MAX_LABEL = 120;

    private final UUID id;
    private final UUID breweryId;
    private final UUID publicationId;
    private final String tokenHash;
    private final SharePermission permission;
    private final String label;
    private final Instant createdAt;
    private final UUID createdBy;
    private final Instant expiresAt;
    private Instant revokedAt;

    private ShareLink(UUID id, UUID breweryId, UUID publicationId, String tokenHash,
            SharePermission permission, String label, Instant createdAt, UUID createdBy,
            Instant expiresAt, Instant revokedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.breweryId = Objects.requireNonNull(breweryId, "cervejaria");
        this.publicationId = Objects.requireNonNull(publicationId, "publicação");
        this.tokenHash = Objects.requireNonNull(tokenHash, "hash do token");
        this.permission = Objects.requireNonNull(permission, "permissão");
        this.label = label;
        this.createdAt = Objects.requireNonNull(createdAt, "criado em");
        this.createdBy = Objects.requireNonNull(createdBy, "criado por");
        this.expiresAt = expiresAt;
        this.revokedAt = revokedAt;
    }

    public static ShareLink create(UUID id, UUID breweryId, UUID publicationId, String tokenHash,
            SharePermission permission, String label, Instant createdAt, UUID createdBy,
            Instant expiresAt) {
        if (tokenHash == null || tokenHash.isBlank()) {
            throw new IllegalArgumentException("o hash do token é obrigatório");
        }
        if (expiresAt != null && !expiresAt.isAfter(createdAt)) {
            // Um link que já nasce vencido não é link, é engano — e um engano que o operador só
            // descobriria quando o outro lado reclamasse.
            throw new IllegalArgumentException("a validade do link precisa ser futura");
        }
        return new ShareLink(id, breweryId, publicationId, tokenHash, permission, clean(label),
                createdAt, createdBy, expiresAt, null);
    }

    public static ShareLink reconstitute(UUID id, UUID breweryId, UUID publicationId, String tokenHash,
            SharePermission permission, String label, Instant createdAt, UUID createdBy,
            Instant expiresAt, Instant revokedAt) {
        return new ShareLink(id, breweryId, publicationId, tokenHash, permission, label, createdAt,
                createdBy, expiresAt, revokedAt);
    }

    /**
     * Corta o link.
     *
     * <p>Revogar duas vezes não é erro: quem clica de novo quer o mesmo resultado, e recusar faria a tela
     * mostrar uma falha para uma operação que já tinha dado certo. O que não se faz é <strong>voltar
     * atrás</strong> — um link revogado fica revogado, porque o motivo de revogar costuma ser que ele
     * chegou a quem não devia, e "desrevogar" reabriria exatamente aquilo.
     */
    public void revoke(Instant at) {
        Objects.requireNonNull(at, "instante");
        if (revokedAt == null) {
            this.revokedAt = at;
        }
    }

    /** Se o link, por si, ainda vale em {@code at} — sem olhar a publicação. */
    public boolean usableAt(Instant at) {
        Objects.requireNonNull(at, "instante");
        if (revokedAt != null) {
            return false;
        }
        return expiresAt == null || at.isBefore(expiresAt);
    }

    /**
     * Se este link dá acesso à publicação em {@code at}.
     *
     * <p>As duas condições, e nesta ordem: o link tem de valer <strong>e</strong> a publicação tem de
     * estar alcançável de fora. É aqui que "o link não eleva visibilidade" deixa de ser comentário e vira
     * comportamento.
     */
    public boolean grantsAccessTo(PublishedRecipe publication, Instant at) {
        Objects.requireNonNull(publication, "publicação");
        if (!publication.id().equals(publicationId)) {
            // Link de outra publicação não abre esta. Parece óbvio, e é justamente o tipo de coisa que
            // um handler distraído deixa passar ao carregar as duas coisas separadamente.
            return false;
        }
        return usableAt(at) && publication.readableByOutsider();
    }

    public boolean allowsComment() {
        return permission == SharePermission.COMMENT;
    }

    private static String clean(String label) {
        if (label == null || label.isBlank()) {
            return null;
        }
        var v = label.strip();
        if (v.length() > MAX_LABEL) {
            throw new IllegalArgumentException("o rótulo do link passa de " + MAX_LABEL + " caracteres");
        }
        return v;
    }

    public UUID id() {
        return id;
    }

    public UUID breweryId() {
        return breweryId;
    }

    public UUID publicationId() {
        return publicationId;
    }

    /** O hash. O valor legível não existe aqui — ele foi mostrado uma vez e esquecido. */
    public String tokenHash() {
        return tokenHash;
    }

    public SharePermission permission() {
        return permission;
    }

    /** Rótulo para o autor lembrar a quem deu — "pro Bruno avaliar". Opcional. */
    public Optional<String> label() {
        return Optional.ofNullable(label);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public UUID createdBy() {
        return createdBy;
    }

    public Optional<Instant> expiresAt() {
        return Optional.ofNullable(expiresAt);
    }

    public Optional<Instant> revokedAt() {
        return Optional.ofNullable(revokedAt);
    }
}
