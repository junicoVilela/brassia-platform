package br.com.brew.brassia.traceability.domain;

import br.com.brew.brassia.traceability.LineageSource.Node;
import br.com.brew.brassia.traceability.LineageSource.NodeType;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Recall aberto sobre um nó da cadeia (FDS-003).
 *
 * <p><strong>O escopo não mora aqui.</strong> Ele é derivado do grafo a cada leitura, como o da
 * quarentena: um envase feito depois da abertura pertence ao recall, e uma lista congelada não o
 * conheceria. O que o critério chama de "escopo reproduzível" é isto — a mesma origem, a mesma
 * profundidade e o mesmo grafo respondem a mesma coisa, e o que mudou desde a abertura aparece
 * declarado em vez de silenciosamente incluído.
 *
 * <p>O que mora aqui é a decisão: quem abriu, por quê, e quando encerrou dizendo o quê.
 */
public final class Recall {

    private static final int MAX_TEXT = 1000;

    private final UUID id;
    private final UUID breweryId;
    private final String code;
    private final NodeType nodeType;
    private final UUID nodeId;
    private final String originLabel;
    private final String reason;
    private final UUID openedBy;
    private final Instant openedAt;
    private RecallStatus status;
    private UUID closedBy;
    private Instant closedAt;
    private String closingSummary;
    private final long version;

    private Recall(UUID id, UUID breweryId, String code, NodeType nodeType, UUID nodeId, String originLabel,
            String reason, UUID openedBy, Instant openedAt, RecallStatus status, UUID closedBy,
            Instant closedAt, String closingSummary, long version) {
        this.id = Objects.requireNonNull(id);
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.code = Objects.requireNonNull(code, "código do recall é obrigatório");
        this.nodeType = Objects.requireNonNull(nodeType, "tipo do nó é obrigatório");
        this.nodeId = Objects.requireNonNull(nodeId, "nó é obrigatório");
        this.originLabel = originLabel;
        this.reason = requireText(reason, "motivo do recall");
        this.openedBy = Objects.requireNonNull(openedBy, "autor da abertura é obrigatório");
        this.openedAt = Objects.requireNonNull(openedAt, "instante da abertura é obrigatório");
        this.status = Objects.requireNonNull(status);
        this.closedBy = closedBy;
        this.closedAt = closedAt;
        this.closingSummary = closingSummary;
        this.version = version;
    }

    public static Recall open(UUID breweryId, String code, Node origin, String reason, UUID actorId,
            Instant at) {
        Objects.requireNonNull(origin, "nó de origem é obrigatório");
        return new Recall(UUID.randomUUID(), breweryId, code, origin.type(), origin.id(), origin.label(),
                reason, actorId, at, RecallStatus.OPEN, null, null, null, 0);
    }

    public static Recall reconstitute(UUID id, UUID breweryId, String code, NodeType nodeType, UUID nodeId,
            String originLabel, String reason, UUID openedBy, Instant openedAt, RecallStatus status,
            UUID closedBy, Instant closedAt, String closingSummary, long version) {
        return new Recall(id, breweryId, code, nodeType, nodeId, originLabel, reason, openedBy, openedAt,
                status, closedBy, closedAt, closingSummary, version);
    }

    /**
     * Encerra o recall.
     *
     * <p>Exige que <strong>todos os destinos tenham sido comunicados</strong>. Encerrar com cliente
     * pendente seria declarar terminada uma operação que deixou cerveja na prateleira de alguém que
     * não foi avisado — e o dossiê passaria a dizer isso para sempre. Quem precisa encerrar sem ter
     * falado com um destino registra a comunicação com o canal e a observação do que aconteceu; o
     * que não se pode é omitir.
     */
    public void close(UUID actorId, String summary, int pendingNotifications, Instant at) {
        if (status != RecallStatus.OPEN) {
            throw new IllegalStateException("o recall já foi encerrado");
        }
        if (pendingNotifications > 0) {
            throw new PendingNotificationsException(pendingNotifications);
        }
        // Valida antes de mudar o estado: um encerramento recusado não pode deixar o agregado
        // meio fechado na memória de quem tentou.
        var closing = requireText(summary, "resumo do encerramento");
        this.closedBy = Objects.requireNonNull(actorId, "autor do encerramento é obrigatório");
        this.closedAt = Objects.requireNonNull(at, "instante do encerramento é obrigatório");
        this.closingSummary = closing;
        this.status = RecallStatus.CLOSED;
    }

    public Node origin() {
        return new Node(nodeType, nodeId, originLabel);
    }

    public boolean open() {
        return status == RecallStatus.OPEN;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " é obrigatório");
        }
        var trimmed = value.trim();
        if (trimmed.length() > MAX_TEXT) {
            throw new IllegalArgumentException(field + " excede " + MAX_TEXT + " caracteres");
        }
        return trimmed;
    }

    public UUID id() { return id; }
    public UUID breweryId() { return breweryId; }
    public String code() { return code; }
    public NodeType nodeType() { return nodeType; }
    public UUID nodeId() { return nodeId; }
    public String originLabel() { return originLabel; }
    public String reason() { return reason; }
    public UUID openedBy() { return openedBy; }
    public Instant openedAt() { return openedAt; }
    public RecallStatus status() { return status; }
    public UUID closedBy() { return closedBy; }
    public Instant closedAt() { return closedAt; }
    public String closingSummary() { return closingSummary; }
    public long version() { return version; }

    public enum RecallStatus {
        OPEN,
        CLOSED
    }
}
