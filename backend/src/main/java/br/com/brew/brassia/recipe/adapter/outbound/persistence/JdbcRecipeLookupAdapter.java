package br.com.brew.brassia.recipe.adapter.outbound.persistence;

import br.com.brew.brassia.recipe.RecipeLookup;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Consulta publicada: devolve a receita apenas quando publicada (snapshot estável). */
@Repository
class JdbcRecipeLookupAdapter implements RecipeLookup {
    private final JdbcClient jdbc;

    JdbcRecipeLookupAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<PublishedRecipe> findPublished(UUID breweryId, UUID recipeId) {
        return jdbc.sql("""
                SELECT id, version, name FROM recipe
                WHERE brewery_id = :brewery AND id = :id AND status = 'PUBLISHED'
                """)
                .param("brewery", breweryId).param("id", recipeId)
                .query((rs, n) -> new PublishedRecipe(
                        rs.getObject("id", UUID.class), (int) rs.getLong("version"), rs.getString("name")))
                .optional();
    }
}
