package br.com.brew.brassia.community.adapter.outbound.persistence;

import br.com.brew.brassia.community.application.port.outbound.RatingRepository;
import br.com.brew.brassia.community.domain.AbuseReport;
import br.com.brew.brassia.community.domain.AlreadyReportedException;
import br.com.brew.brassia.community.domain.Rating;
import br.com.brew.brassia.community.domain.RatingSummary;
import br.com.brew.brassia.community.domain.ReportOutcome;
import br.com.brew.brassia.community.domain.ReportReason;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcRatingRepository implements RatingRepository {

    private final JdbcClient jdbc;

    JdbcRatingRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void rate(Rating rating) {
        // A nota se TROCA. O ON CONFLICT é a consequência da chave composta, e não uma conveniência:
        // acumular faria a média contar quem insistiu mais.
        jdbc.sql("""
                INSERT INTO community_rating (publication_id, user_id, value, rated_at)
                VALUES (:publication, :user, :value, :at)
                ON CONFLICT (publication_id, user_id) DO UPDATE
                SET value = :value, rated_at = :at
                """)
                .param("publication", rating.publicationId()).param("user", rating.userId())
                .param("value", rating.value()).param("at", Timestamp.from(rating.ratedAt()))
                .update();
    }

    @Override
    public Optional<Integer> myRating(UUID publicationId, UUID userId) {
        return jdbc.sql("""
                SELECT value FROM community_rating
                WHERE publication_id = :publication AND user_id = :user
                """)
                .param("publication", publicationId).param("user", userId)
                .query(Integer.class).optional();
    }

    @Override
    public RatingSummary summaryOf(UUID publicationId) {
        // Soma e contagem vêm juntas do banco, e a média é calculada no domínio: é lá que mora a regra
        // de que ela nunca viaja sem a contagem.
        return jdbc.sql("""
                SELECT COALESCE(SUM(value), 0) AS soma, COUNT(*) AS quantos
                FROM community_rating WHERE publication_id = :p
                """)
                .param("p", publicationId)
                .query((rs, row) -> RatingSummary.of(rs.getBigDecimal("soma"), rs.getInt("quantos")))
                .single();
    }

    @Override
    public void report(AbuseReport report) {
        try {
            insert(report);
        } catch (DuplicateKeyException duplicada) {
            // O índice único é a garantia; isto é só a tradução dele para quem clicou duas vezes.
            throw new AlreadyReportedException();
        }
    }

    private void insert(AbuseReport report) {
        jdbc.sql("""
                INSERT INTO community_report (id, publication_id, reporter_user_id, reason, note,
                                              reported_at)
                VALUES (:id, :publication, :reporter, :reason, :note, :at)
                """)
                .param("id", report.id()).param("publication", report.publicationId())
                .param("reporter", report.reporterUserId()).param("reason", report.reason().name())
                .param("note", report.note().orElse(null))
                .param("at", Timestamp.from(report.reportedAt()))
                .update();
    }

    @Override
    public List<AbuseReport> reportsOf(UUID publicationId) {
        return jdbc.sql("""
                SELECT id, publication_id, reporter_user_id, reason, note, reported_at, reviewed_at,
                       reviewed_by, outcome, outcome_note
                FROM community_report WHERE publication_id = :p ORDER BY reported_at DESC
                """)
                .param("p", publicationId)
                .query(JdbcRatingRepository::map).list();
    }

    private static AbuseReport map(ResultSet rs, int row) throws SQLException {
        var reviewedAt = rs.getTimestamp("reviewed_at");
        var outcome = rs.getString("outcome");
        return AbuseReport.reconstitute(rs.getObject("id", UUID.class),
                rs.getObject("publication_id", UUID.class),
                rs.getObject("reporter_user_id", UUID.class),
                ReportReason.valueOf(rs.getString("reason")), rs.getString("note"),
                rs.getTimestamp("reported_at").toInstant(),
                reviewedAt == null ? null : reviewedAt.toInstant(),
                rs.getObject("reviewed_by", UUID.class),
                outcome == null ? null : ReportOutcome.valueOf(outcome), rs.getString("outcome_note"));
    }

    @Override
    public Optional<AbuseReport> findReport(UUID publicationId, UUID reportId) {
        return jdbc.sql("""
                SELECT id, publication_id, reporter_user_id, reason, note, reported_at, reviewed_at,
                       reviewed_by, outcome, outcome_note
                FROM community_report WHERE id = :id AND publication_id = :publication
                """)
                .param("id", reportId).param("publication", publicationId)
                .query(JdbcRatingRepository::map).optional();
    }

    @Override
    public void review(AbuseReport report) {
        // Só o desfecho muda: o motivo, o denunciante e a data da denúncia são imutáveis — reescrevê-los
        // faria a revisão poder alterar a acusação que ela julga.
        jdbc.sql("""
                UPDATE community_report
                SET reviewed_at = :at, reviewed_by = :by, outcome = :outcome, outcome_note = :note
                WHERE id = :id
                """)
                .param("at", report.reviewedAt().map(Timestamp::from).orElse(null))
                .param("by", report.reviewedBy().orElse(null))
                .param("outcome", report.outcome().map(Enum::name).orElse(null))
                .param("note", report.outcomeNote().orElse(null))
                .param("id", report.id())
                .update();
    }
}
