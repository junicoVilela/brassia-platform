package br.com.brew.brassia.sales.adapter.outbound.persistence;

import br.com.brew.brassia.sales.application.port.outbound.ProductRepository;
import br.com.brew.brassia.sales.domain.Product;
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
class JdbcProductRepository implements ProductRepository {

    private final JdbcClient jdbc;

    JdbcProductRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Product product, UUID actorId) {
        jdbc.sql("""
                INSERT INTO sales_product (id, brewery_id, sku, name, recipe_id, container_id, active,
                                           created_by, created_at)
                VALUES (:id, :brewery, :sku, :name, :recipe, :container, :active, :by, :at)
                """)
                .param("id", product.id()).param("brewery", product.breweryId())
                .param("sku", product.sku()).param("name", product.name())
                .param("recipe", product.recipeId()).param("container", product.containerId())
                .param("active", product.isActive()).param("by", actorId)
                .param("at", Timestamp.from(Instant.now()))
                .update();
    }

    @Override
    public void update(Product product) {
        jdbc.sql("""
                UPDATE sales_product SET name = :name, active = :active
                WHERE id = :id AND brewery_id = :brewery
                """)
                .param("name", product.name()).param("active", product.isActive())
                .param("id", product.id()).param("brewery", product.breweryId())
                .update();
    }

    @Override
    public Optional<Product> find(UUID breweryId, UUID id) {
        return jdbc.sql("""
                SELECT id, brewery_id, sku, name, recipe_id, container_id, active
                FROM sales_product WHERE id = :id AND brewery_id = :brewery
                """)
                .param("id", id).param("brewery", breweryId)
                .query(JdbcProductRepository::map).optional();
    }

    @Override
    public List<Product> list(UUID breweryId, boolean onlyActive) {
        return jdbc.sql("""
                SELECT id, brewery_id, sku, name, recipe_id, container_id, active
                FROM sales_product
                WHERE brewery_id = :brewery AND (CAST(:onlyActive AS boolean) = FALSE OR active = TRUE)
                ORDER BY name
                """)
                .param("brewery", breweryId).param("onlyActive", onlyActive)
                .query(JdbcProductRepository::map).list();
    }

    @Override
    public boolean skuTaken(UUID breweryId, String sku) {
        return jdbc.sql("SELECT COUNT(*) FROM sales_product WHERE brewery_id = :brewery AND sku = :sku")
                .param("brewery", breweryId).param("sku", sku)
                .query(Integer.class).single() > 0;
    }

    private static Product map(ResultSet rs, int row) throws SQLException {
        return Product.reconstitute(rs.getObject("id", UUID.class), rs.getObject("brewery_id", UUID.class),
                rs.getString("sku"), rs.getString("name"), rs.getObject("recipe_id", UUID.class),
                rs.getObject("container_id", UUID.class), rs.getBoolean("active"));
    }
}
