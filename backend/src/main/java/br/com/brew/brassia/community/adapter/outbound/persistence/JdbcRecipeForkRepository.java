package br.com.brew.brassia.community.adapter.outbound.persistence;

import br.com.brew.brassia.community.application.port.outbound.RecipeForkRepository;
import br.com.brew.brassia.community.domain.ForkOrigin;
import br.com.brew.brassia.community.domain.RecipeLicense;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcRecipeForkRepository implements RecipeForkRepository {

    private final JdbcClient jdbc;

    JdbcRecipeForkRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(UUID id, UUID breweryId, UUID recipeId, ForkOrigin origin, UUID forkedBy) {
        jdbc.sql("""
                INSERT INTO community_recipe_fork (id, brewery_id, recipe_id, source_publication_id,
                        source_author_name, source_title, source_license, source_recipe_version,
                        forked_by, forked_at)
                VALUES (:id, :brewery, :recipe, :source, :author, :title, :license, :version, :by, :at)
                """)
                .param("id", id).param("brewery", breweryId).param("recipe", recipeId)
                .param("source", origin.sourcePublicationId())
                .param("author", origin.sourceAuthorName()).param("title", origin.sourceTitle())
                .param("license", origin.sourceLicense().name())
                .param("version", origin.sourceRecipeVersion()).param("by", forkedBy)
                .param("at", Timestamp.from(origin.forkedAt()))
                .update();
    }

    @Override
    public Optional<ForkOrigin> originOf(UUID breweryId, UUID recipeId) {
        return jdbc.sql(SELECT + " WHERE brewery_id = :brewery AND recipe_id = :recipe")
                .param("brewery", breweryId).param("recipe", recipeId)
                .query(JdbcRecipeForkRepository::map).optional();
    }

    @Override
    public int countForksOf(UUID publicationId) {
        // Sem cervejaria: a pergunta é "quantos copiaram a minha?", e quem copiou é de outra casa —
        // filtrar por inquilino responderia sempre "as minhas próprias cópias".
        return jdbc.sql("SELECT COUNT(*) FROM community_recipe_fork WHERE source_publication_id = :p")
                .param("p", publicationId)
                .query(Integer.class).single();
    }

    @Override
    public List<ForkOrigin> listOwnForks(UUID breweryId) {
        return jdbc.sql(SELECT + " WHERE brewery_id = :brewery ORDER BY forked_at DESC")
                .param("brewery", breweryId)
                .query(JdbcRecipeForkRepository::map).list();
    }

    private static final String SELECT = """
            SELECT source_publication_id, source_author_name, source_title, source_license,
                   source_recipe_version, forked_at
            FROM community_recipe_fork
            """;

    private static ForkOrigin map(ResultSet rs, int row) throws SQLException {
        return new ForkOrigin(rs.getObject("source_publication_id", UUID.class),
                rs.getString("source_author_name"), rs.getString("source_title"),
                RecipeLicense.valueOf(rs.getString("source_license")),
                rs.getLong("source_recipe_version"), rs.getTimestamp("forked_at").toInstant());
    }
}
