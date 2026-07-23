package br.com.brew.brassia.recipe.adapter.outbound.persistence;

import br.com.brew.brassia.recipe.application.port.outbound.RecipeRepository;
import br.com.brew.brassia.recipe.domain.Recipe;
import br.com.brew.brassia.recipe.domain.RecipeId;
import br.com.brew.brassia.recipe.domain.RecipeItem;
import br.com.brew.brassia.recipe.domain.RecipeName;
import br.com.brew.brassia.recipe.domain.RecipeStage;
import br.com.brew.brassia.recipe.domain.RecipeStatus;
import br.com.brew.brassia.recipe.domain.RecipeTargets;
import br.com.brew.brassia.recipe.domain.RecipeUnit;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcRecipeRepository implements RecipeRepository {
    private static final String SELECT_COLUMNS = """
            SELECT id, brewery_id, name, status, equipment_id, batch_volume_liters, target_og_points,
                   target_ibu, target_color_ebc, target_abv, boil_time_minutes, version
            FROM recipe
            """;

    private final JdbcClient jdbc;

    JdbcRecipeRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean existsByName(UUID breweryId, String normalizedName) {
        return jdbc.sql("SELECT 1 FROM recipe WHERE brewery_id = :brewery AND normalized_name = :name")
                .param("brewery", breweryId).param("name", normalizedName)
                .query(Integer.class).optional().isPresent();
    }

    @Override
    public void insert(Recipe r) {
        var t = r.targets();
        jdbc.sql("""
                INSERT INTO recipe (
                    id, brewery_id, name, normalized_name, status, equipment_id, batch_volume_liters,
                    target_og_points, target_ibu, target_color_ebc, target_abv, boil_time_minutes, version, created_at)
                VALUES (:id, :brewery, :name, :normalized, :status, :equipment, :batch, :og, :ibu, :color, :abv,
                        :boil, :version, :at)
                """)
                .param("id", r.id().value())
                .param("brewery", r.breweryId())
                .param("name", r.name().value())
                .param("normalized", r.name().value().toLowerCase(Locale.ROOT))
                .param("status", r.status().name())
                .param("equipment", r.equipmentId())
                .param("batch", r.batchVolumeLiters())
                .param("og", t.ogPoints())
                .param("ibu", t.ibu())
                .param("color", t.colorEbc())
                .param("abv", t.abv())
                .param("boil", r.boilTimeMinutes())
                .param("version", r.version())
                .param("at", Timestamp.from(Instant.now()))
                .update();

        int position = 0;
        for (var item : r.items()) {
            jdbc.sql("""
                    INSERT INTO recipe_item (
                        id, recipe_id, brewery_id, ingredient_id, stage, quantity, unit, timing_minutes,
                        percentage, position)
                    VALUES (:id, :recipe, :brewery, :ingredient, :stage, :quantity, :unit, :timing, :percentage, :pos)
                    """)
                    .param("id", UUID.randomUUID())
                    .param("recipe", r.id().value())
                    .param("brewery", r.breweryId())
                    .param("ingredient", item.ingredientId())
                    .param("stage", item.stage().name())
                    .param("quantity", item.quantity())
                    .param("unit", item.unit().name())
                    .param("timing", item.timingMinutes())
                    .param("percentage", item.percentage())
                    .param("pos", position++)
                    .update();
        }
    }

    @Override
    public Optional<Recipe> findById(UUID breweryId, UUID id) {
        var raw = jdbc.sql(SELECT_COLUMNS + " WHERE brewery_id = :brewery AND id = :id")
                .param("brewery", breweryId).param("id", id)
                .query((rs, n) -> raw(rs)).optional();
        if (raw.isEmpty()) {
            return Optional.empty();
        }
        var items = jdbc.sql("""
                SELECT ingredient_id, stage, quantity, unit, timing_minutes, percentage
                FROM recipe_item WHERE recipe_id = :id ORDER BY position
                """)
                .param("id", id)
                .query((rs, n) -> new RecipeItem(
                        rs.getObject("ingredient_id", UUID.class),
                        RecipeStage.valueOf(rs.getString("stage")),
                        rs.getBigDecimal("quantity"),
                        RecipeUnit.valueOf(rs.getString("unit")),
                        (Integer) rs.getObject("timing_minutes"),
                        rs.getBigDecimal("percentage")))
                .list();
        return Optional.of(raw.get().build(items));
    }

    @Override
    public List<Recipe> findPage(UUID breweryId, int page, int size) {
        return jdbc.sql(SELECT_COLUMNS + " WHERE brewery_id = :brewery ORDER BY name LIMIT :limit OFFSET :offset")
                .param("brewery", breweryId)
                .param("limit", size)
                .param("offset", (long) page * size)
                .query((rs, n) -> raw(rs).build(List.of()))
                .list();
    }

    @Override
    public long count(UUID breweryId) {
        return jdbc.sql("SELECT count(*) FROM recipe WHERE brewery_id = :brewery")
                .param("brewery", breweryId)
                .query(Long.class).single();
    }

    private static RawRecipe raw(ResultSet rs) throws SQLException {
        return new RawRecipe(
                rs.getObject("id", UUID.class),
                rs.getObject("brewery_id", UUID.class),
                rs.getString("name"),
                rs.getString("status"),
                rs.getObject("equipment_id", UUID.class),
                rs.getBigDecimal("batch_volume_liters"),
                rs.getBigDecimal("target_og_points"),
                rs.getBigDecimal("target_ibu"),
                rs.getBigDecimal("target_color_ebc"),
                rs.getBigDecimal("target_abv"),
                (Integer) rs.getObject("boil_time_minutes"),
                rs.getLong("version"));
    }

    private record RawRecipe(UUID id, UUID breweryId, String name, String status, UUID equipmentId,
            BigDecimal batchVolumeLiters, BigDecimal og, BigDecimal ibu, BigDecimal color, BigDecimal abv,
            Integer boilTimeMinutes, long version) {

        Recipe build(List<RecipeItem> items) {
            return Recipe.reconstitute(new RecipeId(id), breweryId, new RecipeName(name),
                    RecipeStatus.valueOf(status), equipmentId, batchVolumeLiters,
                    new RecipeTargets(og, ibu, color, abv), boilTimeMinutes, items, version);
        }
    }
}
