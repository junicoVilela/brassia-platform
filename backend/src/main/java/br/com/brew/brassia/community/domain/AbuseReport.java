package br.com.brew.brassia.community.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Uma denúncia sobre uma publicação (COM-005).
 *
 * <p><strong>Denunciar registra; não esconde nada sozinho.</strong> Uma denúncia que tirasse o conteúdo
 * do ar na hora seria uma arma: qualquer pessoa derrubaria a receita de um concorrente escrevendo três
 * linhas. O que ela faz é abrir um caso, com data e motivo, para alguém revisar.
 *
 * <p><strong>A mesma pessoa não denuncia a mesma publicação duas vezes pelo mesmo motivo.</strong> Não é
 * limite de opinião — é que a contagem de denúncias é sinal, e um sinal que a mesma pessoa consegue
 * repetir deixa de medir a comunidade e passa a medir a insistência.
 *
 * <p><strong>Revisar é decisão registrada, com direito de resposta preservado:</strong> o texto da
 * denúncia e o desfecho ficam, e a publicação denunciada não é apagada — a mesma regra do "esconder não
 * apaga" da COM-004, pelo mesmo motivo: uma decisão de moderação precisa poder ser revista, e o que foi
 * apagado não se revisa.
 */
public final class AbuseReport {

    private static final int MAX_NOTE = 1000;

    private final UUID id;
    private final UUID publicationId;
    private final UUID reporterUserId;
    private final ReportReason reason;
    private final String note;
    private final Instant reportedAt;
    private Instant reviewedAt;
    private UUID reviewedBy;
    private ReportOutcome outcome;
    private String outcomeNote;

    private AbuseReport(UUID id, UUID publicationId, UUID reporterUserId, ReportReason reason,
            String note, Instant reportedAt, Instant reviewedAt, UUID reviewedBy,
            ReportOutcome outcome, String outcomeNote) {
        this.id = Objects.requireNonNull(id, "id");
        this.publicationId = Objects.requireNonNull(publicationId, "publicação");
        this.reporterUserId = Objects.requireNonNull(reporterUserId, "quem denunciou");
        this.reason = Objects.requireNonNull(reason, "motivo");
        this.note = note;
        this.reportedAt = Objects.requireNonNull(reportedAt, "quando denunciou");
        this.reviewedAt = reviewedAt;
        this.reviewedBy = reviewedBy;
        this.outcome = outcome;
        this.outcomeNote = outcomeNote;
    }

    public static AbuseReport open(UUID id, UUID publicationId, UUID reporterUserId,
            ReportReason reason, String note, Instant reportedAt) {
        if (reason == ReportReason.OTHER && (note == null || note.isBlank())) {
            // "Outro" sem explicação não é denúncia, é ruído: ninguém consegue revisar o que não foi
            // dito.
            throw new IllegalArgumentException("denúncia com motivo \"outro\" precisa de explicação");
        }
        return new AbuseReport(id, publicationId, reporterUserId, reason, clean(note), reportedAt, null,
                null, null, null);
    }

    public static AbuseReport reconstitute(UUID id, UUID publicationId, UUID reporterUserId,
            ReportReason reason, String note, Instant reportedAt, Instant reviewedAt, UUID reviewedBy,
            ReportOutcome outcome, String outcomeNote) {
        return new AbuseReport(id, publicationId, reporterUserId, reason, note, reportedAt, reviewedAt,
                reviewedBy, outcome, outcomeNote);
    }

    /**
     * Fecha o caso.
     *
     * <p>Revisar duas vezes é recusado pelo mesmo motivo de sempre: reescreveria quem decidiu e quando, e
     * é esse registro que torna a moderação auditável — que é o que o critério da história pede.
     */
    public void review(UUID byUserId, Instant at, ReportOutcome outcome, String outcomeNote) {
        Objects.requireNonNull(byUserId, "quem revisou");
        Objects.requireNonNull(at, "instante");
        Objects.requireNonNull(outcome, "desfecho");
        if (isReviewed()) {
            throw new AlreadyReviewedException(this.outcome);
        }
        this.reviewedBy = byUserId;
        this.reviewedAt = at;
        this.outcome = outcome;
        this.outcomeNote = clean(outcomeNote);
    }

    public boolean isReviewed() {
        return reviewedAt != null;
    }

    private static String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        var v = value.strip();
        if (v.length() > MAX_NOTE) {
            throw new IllegalArgumentException("o texto passa de " + MAX_NOTE + " caracteres");
        }
        return v;
    }

    public UUID id() {
        return id;
    }

    public UUID publicationId() {
        return publicationId;
    }

    public UUID reporterUserId() {
        return reporterUserId;
    }

    public ReportReason reason() {
        return reason;
    }

    public Optional<String> note() {
        return Optional.ofNullable(note);
    }

    public Instant reportedAt() {
        return reportedAt;
    }

    public Optional<Instant> reviewedAt() {
        return Optional.ofNullable(reviewedAt);
    }

    public Optional<UUID> reviewedBy() {
        return Optional.ofNullable(reviewedBy);
    }

    public Optional<ReportOutcome> outcome() {
        return Optional.ofNullable(outcome);
    }

    public Optional<String> outcomeNote() {
        return Optional.ofNullable(outcomeNote);
    }
}
