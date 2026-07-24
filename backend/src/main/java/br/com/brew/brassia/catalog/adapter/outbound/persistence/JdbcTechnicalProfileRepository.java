package br.com.brew.brassia.catalog.adapter.outbound.persistence;

import br.com.brew.brassia.catalog.application.port.outbound.TechnicalProfileRepository;
import br.com.brew.brassia.catalog.domain.IngredientTechnicalProfile;
import br.com.brew.brassia.catalog.domain.PropertyRange;
import br.com.brew.brassia.catalog.domain.TechnicalProfileId;
import br.com.brew.brassia.catalog.domain.TechnicalProfileStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcTechnicalProfileRepository implements TechnicalProfileRepository {

    private static final String COLUMNS =
            "id, brewery_id, ingredient_id, manufacturer, origin, form, purpose, laboratory, lab_code, descriptors, "
                    + "source_id, source_name, status, version";

    private final JdbcClient jdbc;

    JdbcTechnicalProfileRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<IngredientTechnicalProfile> findByIngredient(UUID breweryId, UUID ingredientId) {
        return jdbc.sql("SELECT " + COLUMNS + """
                 FROM ingredient_technical_profile
                 WHERE brewery_id = :brewery AND ingredient_id = :ingredient
                """)
                .param("brewery", breweryId).param("ingredient", ingredientId)
                .query((rs, n) -> map(rs)).optional();
    }

    @Override
    public void insert(IngredientTechnicalProfile p) {
        jdbc.sql("""
                INSERT INTO ingredient_technical_profile
                    (id, brewery_id, ingredient_id, manufacturer, origin, form, purpose, laboratory, lab_code,
                     descriptors, source_id, source_name, status, version)
                VALUES (:id, :brewery, :ingredient, :manufacturer, :origin, :form, :purpose, :laboratory, :labCode,
                        :descriptors, :sourceId, :sourceName, :status, :version)
                """)
                .param("id", p.id().value())
                .param("brewery", p.breweryId())
                .param("ingredient", p.ingredientId())
                .param("manufacturer", p.manufacturer())
                .param("origin", p.origin())
                .param("form", p.form())
                .param("purpose", p.purpose())
                .param("laboratory", p.laboratory())
                .param("labCode", p.labCode())
                .param("descriptors", p.descriptors().isEmpty() ? null : String.join("\n", p.descriptors()))
                .param("sourceId", p.sourceId())
                .param("sourceName", p.sourceName())
                .param("status", p.status().name())
                .param("version", p.version())
                .update();
        p.ranges().forEach((property, range) -> jdbc.sql("""
                INSERT INTO ingredient_property_range (id, profile_id, property, min_value, max_value, unit)
                VALUES (:id, :profile, :property, :min, :max, :unit)
                """)
                .param("id", UUID.randomUUID())
                .param("profile", p.id().value())
                .param("property", property)
                .param("min", range.min())
                .param("max", range.max())
                .param("unit", range.unit())
                .update());
    }

    @Override
    public boolean markPublished(UUID breweryId, UUID ingredientId, long expectedVersion) {
        int updated = jdbc.sql("""
                UPDATE ingredient_technical_profile
                SET status = 'PUBLISHED', version = :newVersion, updated_at = now()
                WHERE brewery_id = :brewery AND ingredient_id = :ingredient AND version = :expected
                  AND status = 'DRAFT'
                """)
                .param("newVersion", expectedVersion + 1)
                .param("brewery", breweryId).param("ingredient", ingredientId).param("expected", expectedVersion)
                .update();
        return updated > 0;
    }

    private IngredientTechnicalProfile map(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        String descriptors = rs.getString("descriptors");
        return IngredientTechnicalProfile.reconstitute(
                new TechnicalProfileId(id),
                rs.getObject("brewery_id", UUID.class),
                rs.getObject("ingredient_id", UUID.class),
                rs.getString("manufacturer"), rs.getString("origin"), rs.getString("form"), rs.getString("purpose"),
                rs.getString("laboratory"), rs.getString("lab_code"),
                loadRanges(id),
                descriptors == null ? List.of() : List.of(descriptors.split("\n")),
                rs.getObject("source_id", UUID.class), rs.getString("source_name"),
                TechnicalProfileStatus.valueOf(rs.getString("status")),
                rs.getLong("version"));
    }

    private Map<String, PropertyRange> loadRanges(UUID profileId) {
        Map<String, PropertyRange> ranges = new LinkedHashMap<>();
        jdbc.sql("""
                SELECT property, min_value, max_value, unit FROM ingredient_property_range
                WHERE profile_id = :profile ORDER BY property
                """)
                .param("profile", profileId)
                .query((rs, n) -> {
                    ranges.put(rs.getString("property"), new PropertyRange(rs.getBigDecimal("min_value"),
                            rs.getBigDecimal("max_value"), rs.getString("unit")));
                    return null;
                }).list();
        return ranges;
    }
}
