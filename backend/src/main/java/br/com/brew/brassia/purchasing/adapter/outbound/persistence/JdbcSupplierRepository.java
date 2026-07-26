package br.com.brew.brassia.purchasing.adapter.outbound.persistence;

import br.com.brew.brassia.purchasing.SupplierLookup;
import br.com.brew.brassia.purchasing.application.port.outbound.SupplierRepository;
import br.com.brew.brassia.purchasing.domain.Supplier;
import br.com.brew.brassia.purchasing.domain.SupplierId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcSupplierRepository implements SupplierRepository, SupplierLookup {

    private final JdbcClient jdbc;

    JdbcSupplierRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean existsByCode(UUID breweryId, String code) {
        return jdbc.sql("SELECT 1 FROM supplier WHERE brewery_id = :brewery AND normalized_code = :code")
                .param("brewery", breweryId).param("code", code)
                .query(Integer.class).optional().isPresent();
    }

    @Override
    public void insert(Supplier s) {
        jdbc.sql("""
                INSERT INTO supplier (id, brewery_id, name, code, normalized_code, version)
                VALUES (:id, :brewery, :name, :code, :normalized, :version)
                """)
                .param("id", s.id().value())
                .param("brewery", s.breweryId())
                .param("name", s.name())
                .param("code", s.code())
                .param("normalized", s.code().toLowerCase(Locale.ROOT))
                .param("version", s.version())
                .update();
    }

    @Override
    public List<Supplier> findAll(UUID breweryId) {
        return jdbc.sql("SELECT id, brewery_id, name, code, version FROM supplier "
                        + "WHERE brewery_id = :brewery ORDER BY name")
                .param("brewery", breweryId)
                .query((rs, n) -> Supplier.reconstitute(
                        new SupplierId(rs.getObject("id", UUID.class)),
                        rs.getObject("brewery_id", UUID.class),
                        rs.getString("name"),
                        rs.getString("code"),
                        rs.getLong("version")))
                .list();
    }

    @Override
    public boolean exists(UUID breweryId, UUID supplierId) {
        return jdbc.sql("SELECT 1 FROM supplier WHERE brewery_id = :brewery AND id = :id")
                .param("brewery", breweryId).param("id", supplierId)
                .query(Integer.class).optional().isPresent();
    }
}
