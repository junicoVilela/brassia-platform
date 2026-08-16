package br.com.brew.brassia.community.adapter.outbound.persistence;

import br.com.brew.brassia.community.application.port.outbound.ContributionRepository;
import br.com.brew.brassia.community.domain.Contribution;
import br.com.brew.brassia.community.domain.ContributionKind;
import br.com.brew.brassia.community.domain.ContributionStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcContributionRepository implements ContributionRepository {

    private final JdbcClient jdbc;

    JdbcContributionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(UUID breweryId, Contribution c) {
        jdbc.sql("""
                INSERT INTO community_contribution (id, publication_id, brewery_id, author_user_id,
                        author_display_name, kind, body, context, status, created_at)
                VALUES (:id, :publication, :brewery, :author, :name, :kind, :body, :context, :status, :at)
                """)
                .param("id", c.id()).param("publication", c.publicationId())
                .param("brewery", breweryId).param("author", c.authorUserId())
                .param("name", c.authorDisplayName()).param("kind", c.kind().name())
                .param("body", c.body()).param("context", c.context().orElse(null))
                .param("status", c.status().name()).param("at", Timestamp.from(c.createdAt()))
                .update();
    }

    @Override
    public void update(UUID actingBreweryId, Contribution c) {
        // Só o que muda: decisão e moderação. O texto e a autoria nunca são reescritos — aceitar não
        // reescreve o que foi proposto, e esconder não apaga.
        //
        // O FILTRO DE CERVEJARIA NÃO É PELA LINHA, e sim pela PUBLICAÇÃO. `brewery_id` aqui é de quem
        // escreveu — e quem decide é o dono da publicação, que é de outra casa. Filtrar pela linha
        // recusaria toda decisão legítima; filtrar pela publicação é o que expressa a regra real:
        // "só mexe quem responde por esta publicação".
        //
        // Sem esta subconsulta, a garantia moraria só no handler — o padrão que a OBS-REL-001 encontrou
        // em dez escritas, e que o TenantIsolationTest pegou aqui antes de virar problema.
        jdbc.sql("""
                UPDATE community_contribution
                SET status = :status, decided_at = :decidedAt, decided_by = :decidedBy,
                    decision_note = :note, hidden_at = :hiddenAt
                WHERE id = :id
                  AND publication_id IN (SELECT id FROM community_published_recipe
                                         WHERE brewery_id = :brewery)
                """)
                .param("status", c.status().name())
                .param("decidedAt", c.decidedAt().map(Timestamp::from).orElse(null))
                .param("decidedBy", c.decidedBy().orElse(null))
                .param("note", c.decisionNote().orElse(null))
                .param("hiddenAt", c.hiddenAt().map(Timestamp::from).orElse(null))
                .param("id", c.id()).param("brewery", actingBreweryId)
                .update();
    }

    @Override
    public Optional<Contribution> find(UUID id) {
        return jdbc.sql(SELECT + " WHERE id = :id")
                .param("id", id)
                .query(JdbcContributionRepository::map).optional();
    }

    @Override
    public List<Contribution> listVisible(UUID publicationId) {
        // Sem cervejaria: a conversa de uma publicação é de todos que a alcançam, e a autorização
        // aconteceu antes, na publicação.
        return jdbc.sql(SELECT + """
                 WHERE publication_id = :p AND hidden_at IS NULL
                 ORDER BY created_at DESC
                """)
                .param("p", publicationId)
                .query(JdbcContributionRepository::map).list();
    }

    @Override
    public int countPending(UUID publicationId) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM community_contribution
                WHERE publication_id = :p AND kind = 'SUGGESTION' AND status = 'OPEN'
                  AND hidden_at IS NULL
                """)
                .param("p", publicationId)
                .query(Integer.class).single();
    }

    private static final String SELECT = """
            SELECT id, publication_id, author_user_id, author_display_name, kind, body, context, status,
                   decided_at, decided_by, decision_note, hidden_at, created_at
            FROM community_contribution
            """;

    private static Contribution map(ResultSet rs, int row) throws SQLException {
        var decidedAt = rs.getTimestamp("decided_at");
        var hiddenAt = rs.getTimestamp("hidden_at");
        return Contribution.reconstitute(rs.getObject("id", UUID.class),
                rs.getObject("publication_id", UUID.class),
                rs.getObject("author_user_id", UUID.class), rs.getString("author_display_name"),
                ContributionKind.valueOf(rs.getString("kind")), rs.getString("body"),
                rs.getString("context"), rs.getTimestamp("created_at").toInstant(),
                ContributionStatus.valueOf(rs.getString("status")),
                decidedAt == null ? null : decidedAt.toInstant(),
                rs.getObject("decided_by", UUID.class), rs.getString("decision_note"),
                hiddenAt == null ? null : hiddenAt.toInstant());
    }
}
