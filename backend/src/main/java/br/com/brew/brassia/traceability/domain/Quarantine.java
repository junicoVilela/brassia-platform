package br.com.brew.brassia.traceability.domain;

import br.com.brew.brassia.traceability.LineageSource.Node;
import br.com.brew.brassia.traceability.LineageSource.NodeType;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Quarentena de um nó da cadeia (FDS-002): a investigação declarada, com origem, motivo e alçada.
 *
 * <p><strong>O que se guarda é a origem, não o alcance.</strong> Os descendentes são derivados do
 * grafo no momento da pergunta, pelo mesmo motivo que a genealogia (TRC-001) é derivada: um envase
 * feito depois da abertura precisa nascer bloqueado, e uma lista congelada na abertura não o
 * conheceria. Contenção que envelhece é contenção furada.
 *
 * <p>O rótulo da origem é congelado — ele descreve o que foi bloqueado no dia em que foi, e um
 * lote renomeado depois não pode reescrever a história da investigação.
 */
public final class Quarantine {

    private static final int MAX_REASON = 500;

    private final UUID id;
    private final UUID breweryId;
    private final NodeType nodeType;
    private final UUID nodeId;
    private final String originLabel;
    private final String reason;
    private final UUID openedBy;
    private final Instant openedAt;
    private QuarantineStatus status;
    private UUID releasedBy;
    private Instant releasedAt;
    private String releaseJustification;
    private final long version;

    private Quarantine(UUID id, UUID breweryId, NodeType nodeType, UUID nodeId, String originLabel, String reason,
            UUID openedBy, Instant openedAt, QuarantineStatus status, UUID releasedBy, Instant releasedAt,
            String releaseJustification, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.nodeType = Objects.requireNonNull(nodeType, "tipo do nó é obrigatório");
        this.nodeId = Objects.requireNonNull(nodeId, "nó é obrigatório");
        this.originLabel = originLabel;
        this.reason = requireReason(reason);
        this.openedBy = Objects.requireNonNull(openedBy, "autor da abertura é obrigatório");
        this.openedAt = Objects.requireNonNull(openedAt, "instante da abertura é obrigatório");
        this.status = Objects.requireNonNull(status, "status");
        this.releasedBy = releasedBy;
        this.releasedAt = releasedAt;
        this.releaseJustification = releaseJustification;
        this.version = version;
    }

    public static Quarantine open(UUID breweryId, Node origin, String reason, UUID actorId, Instant at) {
        Objects.requireNonNull(origin, "nó de origem é obrigatório");
        return new Quarantine(UUID.randomUUID(), breweryId, origin.type(), origin.id(), origin.label(), reason,
                actorId, at, QuarantineStatus.OPEN, null, null, null, 0);
    }

    public static Quarantine reconstitute(UUID id, UUID breweryId, NodeType nodeType, UUID nodeId,
            String originLabel, String reason, UUID openedBy, Instant openedAt, QuarantineStatus status,
            UUID releasedBy, Instant releasedAt, String releaseJustification, long version) {
        return new Quarantine(id, breweryId, nodeType, nodeId, originLabel, reason, openedBy, openedAt, status,
                releasedBy, releasedAt, releaseJustification, version);
    }

    /**
     * Libera a quarentena.
     *
     * <p>A justificativa é obrigatória e não é formalidade: liberar é afirmar que a investigação
     * terminou e que o produto pode seguir. Quem assina precisa ter dito por quê — é a metade da
     * alçada que a permissão sozinha não dá.
     */
    public void release(UUID actorId, String justification, Instant at) {
        if (status != QuarantineStatus.OPEN) {
            throw new IllegalStateException("a quarentena já foi liberada");
        }
        if (justification == null || justification.isBlank()) {
            throw new IllegalArgumentException("liberar quarentena exige justificativa");
        }
        if (justification.trim().length() > MAX_REASON) {
            throw new IllegalArgumentException("justificativa excede " + MAX_REASON + " caracteres");
        }
        this.status = QuarantineStatus.RELEASED;
        this.releasedBy = Objects.requireNonNull(actorId, "autor da liberação é obrigatório");
        this.releasedAt = Objects.requireNonNull(at, "instante da liberação é obrigatório");
        this.releaseJustification = justification.trim();
    }

    public Node origin() {
        return new Node(nodeType, nodeId, originLabel);
    }

    public boolean open() {
        return status == QuarantineStatus.OPEN;
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("abrir quarentena exige motivo");
        }
        var trimmed = reason.trim();
        if (trimmed.length() > MAX_REASON) {
            throw new IllegalArgumentException("motivo excede " + MAX_REASON + " caracteres");
        }
        return trimmed;
    }

    public UUID id() { return id; }
    public UUID breweryId() { return breweryId; }
    public NodeType nodeType() { return nodeType; }
    public UUID nodeId() { return nodeId; }
    public String originLabel() { return originLabel; }
    public String reason() { return reason; }
    public UUID openedBy() { return openedBy; }
    public Instant openedAt() { return openedAt; }
    public QuarantineStatus status() { return status; }
    public UUID releasedBy() { return releasedBy; }
    public Instant releasedAt() { return releasedAt; }
    public String releaseJustification() { return releaseJustification; }
    public long version() { return version; }
}
