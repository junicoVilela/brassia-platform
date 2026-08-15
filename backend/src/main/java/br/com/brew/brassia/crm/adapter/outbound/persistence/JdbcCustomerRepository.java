package br.com.brew.brassia.crm.adapter.outbound.persistence;

import br.com.brew.brassia.crm.application.port.outbound.CustomerRepository;
import br.com.brew.brassia.crm.domain.Customer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcCustomerRepository implements CustomerRepository {

    private final JdbcClient jdbc;

    JdbcCustomerRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Customer customer, UUID actorId) {
        jdbc.sql("""
                INSERT INTO crm_customer (id, brewery_id, legal_name, trade_name, tax_id, active,
                                          created_by, created_at)
                VALUES (:id, :brewery, :legal, :trade, :tax, :active, :by, :at)
                """)
                .param("id", customer.id())
                .param("brewery", customer.breweryId())
                .param("legal", customer.legalName())
                .param("trade", customer.tradeName().orElse(null))
                .param("tax", customer.taxId().orElse(null))
                .param("active", customer.isActive())
                .param("by", actorId)
                .param("at", Timestamp.from(customer.createdAt()))
                .update();
    }

    @Override
    public void update(Customer customer) {
        // O filtro por cervejaria não é redundante com o id: é ele que o TenantIsolationTest exige, e
        // é o que impede um handler futuro de escrever numa linha alheia por receber o id do path.
        jdbc.sql("""
                UPDATE crm_customer
                SET legal_name = :legal, trade_name = :trade, active = :active
                WHERE id = :id AND brewery_id = :brewery
                """)
                .param("legal", customer.legalName())
                .param("trade", customer.tradeName().orElse(null))
                .param("active", customer.isActive())
                .param("id", customer.id())
                .param("brewery", customer.breweryId())
                .update();
    }

    @Override
    public Optional<Customer> find(UUID breweryId, UUID id) {
        return jdbc.sql("""
                SELECT id, brewery_id, legal_name, trade_name, tax_id, active, created_at
                FROM crm_customer WHERE id = :id AND brewery_id = :brewery
                """)
                .param("id", id).param("brewery", breweryId)
                .query(JdbcCustomerRepository::map).optional();
    }

    @Override
    public List<Customer> list(UUID breweryId, boolean onlyActive) {
        return jdbc.sql("""
                SELECT id, brewery_id, legal_name, trade_name, tax_id, active, created_at
                FROM crm_customer
                WHERE brewery_id = :brewery AND (CAST(:onlyActive AS boolean) = FALSE OR active = TRUE)
                ORDER BY COALESCE(trade_name, legal_name)
                """)
                .param("brewery", breweryId).param("onlyActive", onlyActive)
                .query(JdbcCustomerRepository::map).list();
    }

    @Override
    public boolean taxIdTaken(UUID breweryId, String taxId, UUID exceptId) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM crm_customer
                WHERE brewery_id = :brewery AND tax_id = :tax
                  AND (CAST(:except AS uuid) IS NULL OR id <> CAST(:except AS uuid))
                """)
                .param("brewery", breweryId).param("tax", taxId).param("except", exceptId)
                .query(Integer.class).single() > 0;
    }

    private static Customer map(ResultSet rs, int row) throws SQLException {
        return Customer.reconstitute(rs.getObject("id", UUID.class), rs.getObject("brewery_id", UUID.class),
                rs.getString("legal_name"), rs.getString("trade_name"), rs.getString("tax_id"),
                rs.getBoolean("active"), rs.getTimestamp("created_at").toInstant());
    }
}
