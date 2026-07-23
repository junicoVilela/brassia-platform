package br.com.brew.brassia.recipe.adapter.outbound.persistence;

import br.com.brew.brassia.recipe.application.port.outbound.RecipeMetricsRepository;
import br.com.brew.brassia.recipe.domain.RecipeMetrics;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcRecipeMetricsRepository implements RecipeMetricsRepository {
    private final JdbcClient jdbc;

    JdbcRecipeMetricsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void upsert(RecipeMetrics m) {
        jdbc.sql("""
                INSERT INTO recipe_metrics (
                    recipe_id, brewery_id, og_points, og_sg, fg_points, fg_sg, abv, ibu, color_ebc,
                    attenuation_percent, method, version, computed_at)
                VALUES (:recipe, :brewery, :ogPoints, :ogSg, :fgPoints, :fgSg, :abv, :ibu, :color, :att,
                        :method, :version, :at)
                ON CONFLICT (recipe_id) DO UPDATE SET
                    og_points = excluded.og_points, og_sg = excluded.og_sg, fg_points = excluded.fg_points,
                    fg_sg = excluded.fg_sg, abv = excluded.abv, ibu = excluded.ibu, color_ebc = excluded.color_ebc,
                    attenuation_percent = excluded.attenuation_percent, method = excluded.method,
                    version = excluded.version, computed_at = excluded.computed_at
                """)
                .param("recipe", m.recipeId())
                .param("brewery", m.breweryId())
                .param("ogPoints", m.ogPoints())
                .param("ogSg", m.ogSg())
                .param("fgPoints", m.fgPoints())
                .param("fgSg", m.fgSg())
                .param("abv", m.abv())
                .param("ibu", m.ibu())
                .param("color", m.colorEbc())
                .param("att", m.attenuationPercent())
                .param("method", m.method())
                .param("version", m.version())
                .param("at", Timestamp.from(Instant.now()))
                .update();
    }

    @Override
    public Optional<RecipeMetrics> findByRecipe(UUID breweryId, UUID recipeId) {
        return jdbc.sql("""
                SELECT recipe_id, brewery_id, og_points, og_sg, fg_points, fg_sg, abv, ibu, color_ebc,
                       attenuation_percent, method, version
                FROM recipe_metrics WHERE brewery_id = :brewery AND recipe_id = :recipe
                """)
                .param("brewery", breweryId).param("recipe", recipeId)
                .query((rs, n) -> map(rs)).optional();
    }

    private static RecipeMetrics map(ResultSet rs) throws SQLException {
        return new RecipeMetrics(
                rs.getObject("recipe_id", UUID.class),
                rs.getObject("brewery_id", UUID.class),
                rs.getBigDecimal("og_points"),
                rs.getBigDecimal("og_sg"),
                rs.getBigDecimal("fg_points"),
                rs.getBigDecimal("fg_sg"),
                rs.getBigDecimal("abv"),
                rs.getBigDecimal("ibu"),
                rs.getBigDecimal("color_ebc"),
                rs.getBigDecimal("attenuation_percent"),
                rs.getString("method"),
                rs.getInt("version"));
    }
}
