package br.com.brew.brassia.catalog.adapter.outbound.persistence;

import br.com.brew.brassia.catalog.application.port.outbound.IngredientRepository;
import br.com.brew.brassia.catalog.domain.Ingredient;
import br.com.brew.brassia.catalog.domain.IngredientCode;
import br.com.brew.brassia.catalog.domain.IngredientId;
import br.com.brew.brassia.catalog.domain.IngredientName;
import br.com.brew.brassia.catalog.domain.IngredientType;
import br.com.brew.brassia.catalog.domain.MeasurementUnit;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcIngredientRepository implements IngredientRepository {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, String>> ATTR_TYPE = new TypeReference<>() {};

    private final JdbcClient jdbc;

    JdbcIngredientRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean existsByCode(UUID breweryId, String code) {
        return jdbc.sql("SELECT 1 FROM catalog_ingredient WHERE brewery_id = :brewery AND code = :code")
                .param("brewery", breweryId)
                .param("code", code)
                .query(Integer.class)
                .optional()
                .isPresent();
    }

    @Override
    public void insert(Ingredient ingredient) {
        jdbc.sql("""
                INSERT INTO catalog_ingredient (
                    id, brewery_id, type, code, name, use_unit, purchase_unit, attributes, active, version,
                    created_at, updated_at)
                VALUES (:id, :brewery, :type, :code, :name, :use, :purchase, CAST(:attributes AS jsonb),
                        :active, :version, :at, :at)
                """)
                .param("id", ingredient.id().value())
                .param("brewery", ingredient.breweryId())
                .param("type", ingredient.type().name())
                .param("code", ingredient.code().value())
                .param("name", ingredient.name().value())
                .param("use", ingredient.useUnit().name())
                .param("purchase", ingredient.purchaseUnit().name())
                .param("attributes", toJson(ingredient.attributes()))
                .param("active", ingredient.active())
                .param("version", ingredient.version())
                .param("at", Timestamp.from(Instant.now()))
                .update();
    }

    @Override
    public boolean update(Ingredient ingredient, long expectedVersion) {
        int updated = jdbc.sql("""
                UPDATE catalog_ingredient
                SET name = :name, use_unit = :use, purchase_unit = :purchase,
                    attributes = CAST(:attributes AS jsonb), version = :newVersion, updated_at = :at
                WHERE id = :id AND brewery_id = :brewery AND version = :expected
                """)
                .param("name", ingredient.name().value())
                .param("use", ingredient.useUnit().name())
                .param("purchase", ingredient.purchaseUnit().name())
                .param("attributes", toJson(ingredient.attributes()))
                .param("newVersion", expectedVersion + 1)
                .param("at", Timestamp.from(Instant.now()))
                .param("id", ingredient.id().value())
                .param("brewery", ingredient.breweryId())
                .param("expected", expectedVersion)
                .update();
        return updated > 0;
    }

    @Override
    public Optional<Ingredient> findById(UUID breweryId, UUID id) {
        return jdbc.sql("""
                SELECT id, brewery_id, type, code, name, use_unit, purchase_unit, attributes, active, version
                FROM catalog_ingredient WHERE brewery_id = :brewery AND id = :id
                """)
                .param("brewery", breweryId)
                .param("id", id)
                .query((rs, n) -> map(rs))
                .optional();
    }

    @Override
    public List<Ingredient> findPage(UUID breweryId, IngredientType type, int page, int size) {
        var sql = new StringBuilder("""
                SELECT id, brewery_id, type, code, name, use_unit, purchase_unit, attributes, active, version
                FROM catalog_ingredient WHERE brewery_id = :brewery
                """);
        if (type != null) {
            sql.append(" AND type = :type");
        }
        sql.append(" ORDER BY code LIMIT :limit OFFSET :offset");
        var spec = jdbc.sql(sql.toString())
                .param("brewery", breweryId)
                .param("limit", size)
                .param("offset", (long) page * size);
        if (type != null) {
            spec = spec.param("type", type.name());
        }
        return spec.query((rs, n) -> map(rs)).list();
    }

    @Override
    public long count(UUID breweryId, IngredientType type) {
        var sql = new StringBuilder("SELECT count(*) FROM catalog_ingredient WHERE brewery_id = :brewery");
        if (type != null) {
            sql.append(" AND type = :type");
        }
        var spec = jdbc.sql(sql.toString()).param("brewery", breweryId);
        if (type != null) {
            spec = spec.param("type", type.name());
        }
        return spec.query(Long.class).single();
    }

    private static Ingredient map(ResultSet rs) throws SQLException {
        return Ingredient.reconstitute(
                new IngredientId(rs.getObject("id", UUID.class)),
                rs.getObject("brewery_id", UUID.class),
                IngredientType.valueOf(rs.getString("type")),
                new IngredientCode(rs.getString("code")),
                new IngredientName(rs.getString("name")),
                MeasurementUnit.valueOf(rs.getString("use_unit")),
                MeasurementUnit.valueOf(rs.getString("purchase_unit")),
                fromJson(rs.getString("attributes")),
                rs.getBoolean("active"),
                rs.getLong("version"));
    }

    private static String toJson(Map<String, String> attributes) {
        try {
            return JSON.writeValueAsString(attributes);
        } catch (Exception e) {
            throw new IllegalStateException("falha ao serializar atributos", e);
        }
    }

    private static Map<String, String> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return JSON.readValue(json, ATTR_TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("falha ao ler atributos", e);
        }
    }
}
