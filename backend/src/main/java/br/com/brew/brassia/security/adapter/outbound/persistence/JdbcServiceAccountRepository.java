package br.com.brew.brassia.security.adapter.outbound.persistence;

import br.com.brew.brassia.security.application.port.outbound.ServiceAccountRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcServiceAccountRepository implements ServiceAccountRepository {
    private final JdbcClient jdbc;

    JdbcServiceAccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UUID create(UUID breweryId, String code, String name) {
        var id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO service_account (id, brewery_id, code, name)
                VALUES (:id, :breweryId, :code, :name)
                """)
                .param("id", id)
                .param("breweryId", breweryId)
                .param("code", code)
                .param("name", name)
                .update();
        return id;
    }

    @Override
    public List<ServiceAccountView> listByBrewery(UUID breweryId) {
        return jdbc.sql("""
                SELECT id, brewery_id, code, name, active FROM service_account
                WHERE brewery_id = :breweryId ORDER BY created_at
                """)
                .param("breweryId", breweryId)
                .query((rs, n) -> new ServiceAccountView(
                        rs.getObject("id", UUID.class),
                        rs.getObject("brewery_id", UUID.class),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getBoolean("active")))
                .list();
    }

    @Override
    public Optional<ServiceAccountView> findById(UUID id) {
        return jdbc.sql("""
                SELECT id, brewery_id, code, name, active FROM service_account WHERE id = :id
                """)
                .param("id", id)
                .query((rs, n) -> new ServiceAccountView(
                        rs.getObject("id", UUID.class),
                        rs.getObject("brewery_id", UUID.class),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getBoolean("active")))
                .optional();
    }

    @Override
    public boolean belongsToBrewery(UUID id, UUID breweryId) {
        return jdbc.sql("SELECT count(*) FROM service_account WHERE id = :id AND brewery_id = :breweryId")
                .param("id", id)
                .param("breweryId", breweryId)
                .query(Long.class).single() > 0;
    }
}
