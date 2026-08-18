package br.com.brew.brassia.sales.adapter.inbound.gateway;

import br.com.brew.brassia.sales.CustomerActivityLookup;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Responde o último pedido do cliente, cancelado ou não (DUV-CRM-001). */
@Component
class JdbcCustomerActivityAdapter implements CustomerActivityLookup {

    private final JdbcClient jdbc;

    JdbcCustomerActivityAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<LocalDate> lastOrderOn(UUID breweryId, UUID customerId) {
        // Sem filtro de status: aqui a pergunta é sobre relacionamento, e um pedido cancelado é contato
        // do cliente com a casa.
        return jdbc.sql("""
                SELECT max(placed_on) FROM sales_order
                WHERE brewery_id = :brewery AND customer_id = :customer
                """)
                .param("brewery", breweryId).param("customer", customerId)
                .query(LocalDate.class).optional();
    }
}
