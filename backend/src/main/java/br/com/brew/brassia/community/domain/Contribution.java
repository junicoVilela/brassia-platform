package br.com.brew.brassia.community.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Um comentário ou uma sugestão numa publicação (COM-004).
 *
 * <p><strong>Aceitar uma sugestão não altera coisa nenhuma, e essa é a decisão central da história.</strong>
 * Duas razões, e as duas são estruturais:
 *
 * <ul>
 *   <li><strong>O retrato publicado é congelado</strong> (COM-001). Aplicar uma sugestão nele faria o que
 *       o público já leu mudar depois — exatamente o que a decisão de congelar existe para impedir.</li>
 *   <li><strong>A receita de verdade é privada.</strong> Deixar que texto de alguém de fora reescreva a
 *       receita da casa seria dar a estranhos uma chave que nem o link de colaboração dá.</li>
 * </ul>
 *
 * <p>Então aceitar é <strong>registrar concordância</strong>: fica escrito que o autor achou a sugestão
 * boa, com data e nome. Aplicar é ato dele, na receita dele, e produz uma versão nova — que ele pode
 * publicar quando quiser. A cadeia continua auditável sem que ninguém mexa no que é do outro.
 *
 * <p><strong>Recusar não apaga.</strong> Uma sugestão recusada continua visível, com a decisão ao lado:
 * é isso que evita a mesma sugestão voltar três vezes, e é o que torna a conversa um histórico em vez de
 * uma caixa de entrada.
 *
 * <p><strong>O nome de quem escreveu é congelado</strong>, como na atribuição do fork: a autoria de um
 * comentário não muda quando a pessoa troca o nome de exibição.
 */
public final class Contribution {

    private static final int MAX_BODY = 2000;
    private static final int MAX_CONTEXT = 120;
    private static final int MAX_DECISION_NOTE = 500;

    private final UUID id;
    private final UUID publicationId;
    private final UUID authorUserId;
    private final String authorDisplayName;
    private final ContributionKind kind;
    private final String body;
    private final String context;
    private final Instant createdAt;
    private ContributionStatus status;
    private Instant decidedAt;
    private UUID decidedBy;
    private String decisionNote;
    private Instant hiddenAt;

    private Contribution(UUID id, UUID publicationId, UUID authorUserId, String authorDisplayName,
            ContributionKind kind, String body, String context, Instant createdAt,
            ContributionStatus status, Instant decidedAt, UUID decidedBy, String decisionNote,
            Instant hiddenAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.publicationId = Objects.requireNonNull(publicationId, "publicação");
        this.authorUserId = Objects.requireNonNull(authorUserId, "autor");
        this.authorDisplayName = Objects.requireNonNull(authorDisplayName, "nome do autor");
        this.kind = Objects.requireNonNull(kind, "tipo");
        this.body = body;
        this.context = context;
        this.createdAt = Objects.requireNonNull(createdAt, "criado em");
        this.status = Objects.requireNonNull(status, "situação");
        this.decidedAt = decidedAt;
        this.decidedBy = decidedBy;
        this.decisionNote = decisionNote;
        this.hiddenAt = hiddenAt;
    }

    /**
     * @param context onde na receita o comentário se refere — "Malte Pilsen", "fervura". Opcional, e é
     *                o que torna o comentário <em>contextual</em> em vez de um mural
     */
    public static Contribution write(UUID id, UUID publicationId, UUID authorUserId,
            String authorDisplayName, ContributionKind kind, String body, String context,
            Instant createdAt) {
        return new Contribution(id, publicationId, authorUserId, authorDisplayName, kind,
                requiredBody(body), optionalContext(context), createdAt, ContributionStatus.OPEN, null,
                null, null, null);
    }

    public static Contribution reconstitute(UUID id, UUID publicationId, UUID authorUserId,
            String authorDisplayName, ContributionKind kind, String body, String context,
            Instant createdAt, ContributionStatus status, Instant decidedAt, UUID decidedBy,
            String decisionNote, Instant hiddenAt) {
        return new Contribution(id, publicationId, authorUserId, authorDisplayName, kind, body, context,
                createdAt, status, decidedAt, decidedBy, decisionNote, hiddenAt);
    }

    /**
     * O autor concorda com a sugestão.
     *
     * <p><strong>Não altera a receita nem o retrato publicado.</strong> Registra que ele achou boa —
     * aplicar é ato dele, na receita dele, e vira versão nova.
     */
    public void accept(UUID byUserId, Instant at, String note) {
        decide(ContributionStatus.ACCEPTED, byUserId, at, note);
    }

    /** O autor não vai seguir. A sugestão continua visível, com a decisão ao lado. */
    public void decline(UUID byUserId, Instant at, String note) {
        decide(ContributionStatus.DECLINED, byUserId, at, note);
    }

    private void decide(ContributionStatus newStatus, UUID byUserId, Instant at, String note) {
        Objects.requireNonNull(byUserId, "quem decidiu");
        Objects.requireNonNull(at, "instante");
        if (kind != ContributionKind.SUGGESTION) {
            // Um comentário não se aceita nem se recusa: ele não pediu nada. Deixar passar faria a tela
            // oferecer dois botões sem sentido, e a contagem de "pendentes" incluir elogios.
            throw new NotDecidableException(kind);
        }
        if (status != ContributionStatus.OPEN) {
            // Decidir duas vezes reescreveria quem decidiu e quando — e é justamente esse registro que
            // torna a conversa auditável.
            throw new AlreadyDecidedException(status);
        }
        this.status = newStatus;
        this.decidedBy = byUserId;
        this.decidedAt = at;
        this.decisionNote = optionalNote(note);
    }

    /**
     * Esconde da lista pública (COM-005).
     *
     * <p>Esconder não apaga: a moderação precisa poder ser revista, e um texto apagado não se revisa.
     */
    public void hide(Instant at) {
        this.hiddenAt = Objects.requireNonNull(at, "instante");
    }

    public boolean isVisible() {
        return hiddenAt == null;
    }

    public boolean isPending() {
        return kind == ContributionKind.SUGGESTION && status == ContributionStatus.OPEN;
    }

    private static String requiredBody(String body) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("o texto é obrigatório");
        }
        var clean = body.strip();
        if (clean.length() > MAX_BODY) {
            throw new IllegalArgumentException("o texto passa de " + MAX_BODY + " caracteres");
        }
        return clean;
    }

    private static String optionalContext(String context) {
        return trimTo(context, MAX_CONTEXT, "o contexto");
    }

    private static String optionalNote(String note) {
        return trimTo(note, MAX_DECISION_NOTE, "a nota da decisão");
    }

    private static String trimTo(String value, int max, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        var clean = value.strip();
        if (clean.length() > max) {
            throw new IllegalArgumentException(field + " passa de " + max + " caracteres");
        }
        return clean;
    }

    public UUID id() {
        return id;
    }

    public UUID publicationId() {
        return publicationId;
    }

    public UUID authorUserId() {
        return authorUserId;
    }

    /** Congelado, como a atribuição do fork: autoria não muda quando a pessoa troca o nome. */
    public String authorDisplayName() {
        return authorDisplayName;
    }

    public ContributionKind kind() {
        return kind;
    }

    public String body() {
        return body;
    }

    public Optional<String> context() {
        return Optional.ofNullable(context);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public ContributionStatus status() {
        return status;
    }

    public Optional<Instant> decidedAt() {
        return Optional.ofNullable(decidedAt);
    }

    public Optional<UUID> decidedBy() {
        return Optional.ofNullable(decidedBy);
    }

    public Optional<String> decisionNote() {
        return Optional.ofNullable(decisionNote);
    }

    public Optional<Instant> hiddenAt() {
        return Optional.ofNullable(hiddenAt);
    }
}
