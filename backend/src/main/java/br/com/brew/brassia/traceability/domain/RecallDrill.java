package br.com.brew.brassia.traceability.domain;

import br.com.brew.brassia.traceability.LineageSource.Node;
import br.com.brew.brassia.traceability.LineageSource.NodeType;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Simulado de recall (FDS-004): o exercício de localizar um lote, cronometrado.
 *
 * <p><strong>Não afeta estoque real.</strong> Não cria expedição, não move saldo, não abre
 * quarentena e não gera pendência de comunicação — lê o mesmo grafo que o recall de verdade leria e
 * mede a casa respondendo "onde está". É a diferença entre treinar e recolher.
 *
 * <p><strong>O tempo medido é o da cervejaria, não o do sistema.</strong> Derivar o escopo leva
 * milissegundos e não diz nada; o que a norma cobra é quantas horas a equipe levou para localizar o
 * produto. Por isso o simulado tem começo e fim declarados por gente.
 *
 * <p><strong>O resultado é congelado, e aqui a cópia é a coisa certa</strong> — pelo motivo oposto
 * ao do escopo do recall. Escopo é sobre o presente, e por isso é derivado; o resultado do simulado
 * é uma medição daquele dia. Recalculá-lo depois responderia sobre outro dia e apagaria o exercício.
 */
public final class RecallDrill {

    private static final int MAX_SUMMARY = 1000;
    private static final int MAX_ACTIONS = 2000;

    private final UUID id;
    private final UUID breweryId;
    private final String code;
    private final NodeType nodeType;
    private final UUID nodeId;
    private final String originLabel;
    private final String note;
    private final UUID startedBy;
    private final Instant startedAt;
    private DrillStatus status;
    private UUID finishedBy;
    private Instant finishedAt;
    private Integer unitsInScope;
    private Integer unitsLocated;
    private Integer destinationsReached;
    private Integer gapsFound;
    private String summary;
    private String correctiveActions;

    private RecallDrill(UUID id, UUID breweryId, String code, NodeType nodeType, UUID nodeId,
            String originLabel, String note, UUID startedBy, Instant startedAt, DrillStatus status,
            UUID finishedBy, Instant finishedAt, Integer unitsInScope, Integer unitsLocated,
            Integer destinationsReached, Integer gapsFound, String summary, String correctiveActions) {
        this.id = Objects.requireNonNull(id);
        this.breweryId = Objects.requireNonNull(breweryId, "breweryId");
        this.code = Objects.requireNonNull(code, "código do simulado é obrigatório");
        this.nodeType = Objects.requireNonNull(nodeType, "tipo do nó é obrigatório");
        this.nodeId = Objects.requireNonNull(nodeId, "nó é obrigatório");
        this.originLabel = originLabel;
        this.note = note;
        this.startedBy = Objects.requireNonNull(startedBy, "autor do início é obrigatório");
        this.startedAt = Objects.requireNonNull(startedAt, "instante do início é obrigatório");
        this.status = Objects.requireNonNull(status);
        this.finishedBy = finishedBy;
        this.finishedAt = finishedAt;
        this.unitsInScope = unitsInScope;
        this.unitsLocated = unitsLocated;
        this.destinationsReached = destinationsReached;
        this.gapsFound = gapsFound;
        this.summary = summary;
        this.correctiveActions = correctiveActions;
    }

    public static RecallDrill start(UUID breweryId, String code, Node origin, String note, UUID actorId,
            Instant at) {
        Objects.requireNonNull(origin, "nó de origem é obrigatório");
        return new RecallDrill(UUID.randomUUID(), breweryId, code, origin.type(), origin.id(),
                origin.label(), trimToNull(note), actorId, at, DrillStatus.RUNNING, null, null, null, null,
                null, null, null, null);
    }

    public static RecallDrill reconstitute(UUID id, UUID breweryId, String code, NodeType nodeType,
            UUID nodeId, String originLabel, String note, UUID startedBy, Instant startedAt,
            DrillStatus status, UUID finishedBy, Instant finishedAt, Integer unitsInScope,
            Integer unitsLocated, Integer destinationsReached, Integer gapsFound, String summary,
            String correctiveActions) {
        return new RecallDrill(id, breweryId, code, nodeType, nodeId, originLabel, note, startedBy,
                startedAt, status, finishedBy, finishedAt, unitsInScope, unitsLocated, destinationsReached,
                gapsFound, summary, correctiveActions);
    }

    /**
     * Encerra o simulado com o que a equipe encontrou.
     *
     * <p>{@code unitsLocated} é declarado por gente, e não contado pelo sistema: o sistema sabe
     * quantas unidades <em>deveriam</em> estar em cada destino, e o simulado existe justamente para
     * descobrir que às vezes elas não estão. Contar sozinho daria 100% sempre e não mediria nada.
     *
     * <p>Localizar mais do que existe no escopo é recusado — sairia um percentual acima de 100%, que
     * é sinal de erro de contagem e não de excelência.
     */
    public void finish(UUID actorId, int unitsInScope, int unitsLocated, int destinationsReached,
            int gapsFound, String summary, String correctiveActions, Instant at) {
        if (status != DrillStatus.RUNNING) {
            throw new IllegalStateException("este simulado já foi encerrado");
        }
        if (unitsLocated < 0) {
            throw new IllegalArgumentException("unidades localizadas não podem ser negativas");
        }
        if (unitsLocated > unitsInScope) {
            throw new IllegalArgumentException(
                    "unidades localizadas (" + unitsLocated + ") acima do escopo (" + unitsInScope + ")");
        }
        var closing = requireText(summary);
        this.finishedBy = Objects.requireNonNull(actorId, "autor do encerramento é obrigatório");
        this.finishedAt = Objects.requireNonNull(at, "instante do encerramento é obrigatório");
        this.unitsInScope = unitsInScope;
        this.unitsLocated = unitsLocated;
        this.destinationsReached = destinationsReached;
        this.gapsFound = gapsFound;
        this.summary = closing;
        this.correctiveActions = trimToNull(correctiveActions, MAX_ACTIONS);
        this.status = DrillStatus.FINISHED;
    }

    /**
     * Percentual localizado — o número que o relatório apresenta.
     *
     * <p>Escopo zero devolve vazio, e não 100%: não localizar nada porque não havia nada não é
     * cobertura perfeita, é simulado sem objeto.
     */
    public Integer locatedPercent() {
        if (unitsInScope == null || unitsLocated == null || unitsInScope == 0) {
            return null;
        }
        return unitsLocated * 100 / unitsInScope;
    }

    /** Quanto a cervejaria levou. Enquanto corre, é o tempo até agora. */
    public Duration elapsed(Instant now) {
        return Duration.between(startedAt, finishedAt == null ? now : finishedAt);
    }

    public boolean running() {
        return status == DrillStatus.RUNNING;
    }

    public Node origin() {
        return new Node(nodeType, nodeId, originLabel);
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("resumo do simulado é obrigatório");
        }
        var trimmed = value.trim();
        if (trimmed.length() > MAX_SUMMARY) {
            throw new IllegalArgumentException("resumo excede " + MAX_SUMMARY + " caracteres");
        }
        return trimmed;
    }

    private static String trimToNull(String value) {
        return trimToNull(value, 500);
    }

    private static String trimToNull(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        var trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new IllegalArgumentException("texto excede " + max + " caracteres");
        }
        return trimmed;
    }

    public UUID id() { return id; }
    public UUID breweryId() { return breweryId; }
    public String code() { return code; }
    public NodeType nodeType() { return nodeType; }
    public UUID nodeId() { return nodeId; }
    public String originLabel() { return originLabel; }
    public String note() { return note; }
    public UUID startedBy() { return startedBy; }
    public Instant startedAt() { return startedAt; }
    public DrillStatus status() { return status; }
    public UUID finishedBy() { return finishedBy; }
    public Instant finishedAt() { return finishedAt; }
    public Integer unitsInScope() { return unitsInScope; }
    public Integer unitsLocated() { return unitsLocated; }
    public Integer destinationsReached() { return destinationsReached; }
    public Integer gapsFound() { return gapsFound; }
    public String summary() { return summary; }
    public String correctiveActions() { return correctiveActions; }

    public enum DrillStatus {
        RUNNING,
        FINISHED
    }
}
