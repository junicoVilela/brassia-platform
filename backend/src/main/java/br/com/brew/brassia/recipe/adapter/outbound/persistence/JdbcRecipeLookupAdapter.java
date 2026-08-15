package br.com.brew.brassia.recipe.adapter.outbound.persistence;

import br.com.brew.brassia.recipe.RecipeLookup;
import java.math.BigDecimal;
import java.util.List;
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

    @Override
    public Optional<ExpectedLoss> expectedLoss(UUID breweryId, UUID recipeId) {
        // Sem filtro de status: o lote guarda a versão publicada que usou, e é contra ela que a perda é
        // comparada. Exigir PUBLISHED aqui perderia a comparação de um lote cuja receita já gerou versão
        // nova — que é o caso normal de uma cervejaria que ajusta a fórmula.
        return jdbc.sql("SELECT transfer_loss_percent, packaging_loss_percent FROM recipe "
                        + "WHERE brewery_id = :brewery AND id = :id")
                .param("brewery", breweryId).param("id", recipeId)
                .query((rs, n) -> new ExpectedLoss(rs.getBigDecimal("transfer_loss_percent"),
                        rs.getBigDecimal("packaging_loss_percent")))
                .optional();
    }

    @Override
    public Optional<PublishedComposition> findPublishedComposition(UUID breweryId, UUID recipeId) {
        var header = jdbc.sql("""
                SELECT id, version, batch_volume_liters FROM recipe
                WHERE brewery_id = :brewery AND id = :id AND status = 'PUBLISHED'
                """)
                .param("brewery", breweryId).param("id", recipeId)
                .query((rs, n) -> new Object[] {
                        rs.getObject("id", UUID.class), rs.getLong("version"), rs.getBigDecimal("batch_volume_liters")})
                .optional();
        if (header.isEmpty()) {
            return Optional.empty();
        }
        var items = jdbc.sql("""
                SELECT ingredient_id, stage, quantity, unit
                FROM recipe_item WHERE recipe_id = :id AND brewery_id = :brewery ORDER BY position
                """)
                .param("id", recipeId).param("brewery", breweryId)
                .query((rs, n) -> new CompositionItem(
                        rs.getObject("ingredient_id", UUID.class), rs.getString("stage"),
                        rs.getBigDecimal("quantity"), rs.getString("unit")))
                .list();
        var row = header.get();
        return Optional.of(new PublishedComposition(
                (UUID) row[0], (int) (long) (Long) row[1], (BigDecimal) row[2], List.copyOf(items)));
    }

    @Override
    public Optional<PublishedForOrder> findPublishedForOrder(UUID breweryId, UUID recipeId) {
        var header = jdbc.sql("""
                SELECT id, version, name, equipment_id, batch_volume_liters FROM recipe
                WHERE brewery_id = :brewery AND id = :id AND status = 'PUBLISHED'
                """)
                .param("brewery", breweryId).param("id", recipeId)
                .query((rs, n) -> new PublishedForOrder(
                        rs.getObject("id", UUID.class), (int) rs.getLong("version"), rs.getString("name"),
                        rs.getObject("equipment_id", UUID.class), rs.getBigDecimal("batch_volume_liters"),
                        Optional.empty()))
                .optional();
        if (header.isEmpty()) {
            return Optional.empty();
        }
        var metrics = jdbc.sql("""
                SELECT og_sg, fg_sg, abv, ibu, color_ebc FROM recipe_metrics WHERE recipe_id = :id
                """)
                .param("id", recipeId)
                .query((rs, n) -> new Metrics(rs.getBigDecimal("og_sg"), rs.getBigDecimal("fg_sg"),
                        rs.getBigDecimal("abv"), rs.getBigDecimal("ibu"), rs.getBigDecimal("color_ebc")))
                .optional();
        var h = header.get();
        return Optional.of(new PublishedForOrder(
                h.id(), h.version(), h.name(), h.equipmentId(), h.batchVolumeLiters(), metrics));
    }
}
