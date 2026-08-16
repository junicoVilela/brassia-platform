package br.com.brew.brassia.community.adapter.outbound.persistence;

import br.com.brew.brassia.community.application.port.outbound.ShareLinkRepository;
import br.com.brew.brassia.community.domain.ShareLink;
import br.com.brew.brassia.community.domain.SharePermission;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcShareLinkRepository implements ShareLinkRepository {

    private final JdbcClient jdbc;

    JdbcShareLinkRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(ShareLink link) {
        jdbc.sql("""
                INSERT INTO community_share_link (id, brewery_id, publication_id, token_hash, permission,
                        label, created_by, created_at, expires_at, revoked_at)
                VALUES (:id, :brewery, :publication, :hash, :permission, :label, :by, :at, :expires, NULL)
                """)
                .param("id", link.id()).param("brewery", link.breweryId())
                .param("publication", link.publicationId()).param("hash", link.tokenHash())
                .param("permission", link.permission().name()).param("label", link.label().orElse(null))
                .param("by", link.createdBy()).param("at", Timestamp.from(link.createdAt()))
                .param("expires", link.expiresAt().map(Timestamp::from).orElse(null))
                .update();
    }

    @Override
    public void revoke(UUID breweryId, UUID id, Instant at) {
        // Só marca o que ainda não estava revogado: revogar de novo não pode reescrever a data, que é o
        // registro de quando o acesso foi cortado.
        jdbc.sql("""
                UPDATE community_share_link SET revoked_at = :at
                WHERE id = :id AND brewery_id = :brewery AND revoked_at IS NULL
                """)
                .param("at", Timestamp.from(at)).param("id", id).param("brewery", breweryId)
                .update();
    }

    @Override
    public Optional<ShareLink> findByTokenHash(String tokenHash) {
        // Sem cervejaria no filtro: quem chega com um link não tem cervejaria no contexto — é justamente
        // o caso de alguém de fora. O escopo vem da linha encontrada.
        return jdbc.sql(SELECT + " WHERE token_hash = :hash")
                .param("hash", tokenHash)
                .query(JdbcShareLinkRepository::map).optional();
    }

    @Override
    public Optional<ShareLink> findOwned(UUID breweryId, UUID id) {
        return jdbc.sql(SELECT + " WHERE id = :id AND brewery_id = :brewery")
                .param("id", id).param("brewery", breweryId)
                .query(JdbcShareLinkRepository::map).optional();
    }

    @Override
    public List<ShareLink> listOfPublication(UUID breweryId, UUID publicationId) {
        return jdbc.sql(SELECT + """
                 WHERE brewery_id = :brewery AND publication_id = :publication
                 ORDER BY created_at DESC
                """)
                .param("brewery", breweryId).param("publication", publicationId)
                .query(JdbcShareLinkRepository::map).list();
    }

    private static final String SELECT = """
            SELECT id, brewery_id, publication_id, token_hash, permission, label, created_by, created_at,
                   expires_at, revoked_at
            FROM community_share_link
            """;

    private static ShareLink map(ResultSet rs, int row) throws SQLException {
        var expires = rs.getTimestamp("expires_at");
        var revoked = rs.getTimestamp("revoked_at");
        return ShareLink.reconstitute(rs.getObject("id", UUID.class),
                rs.getObject("brewery_id", UUID.class), rs.getObject("publication_id", UUID.class),
                rs.getString("token_hash"), SharePermission.valueOf(rs.getString("permission")),
                rs.getString("label"), rs.getTimestamp("created_at").toInstant(),
                rs.getObject("created_by", UUID.class),
                expires == null ? null : expires.toInstant(),
                revoked == null ? null : revoked.toInstant());
    }
}
