package br.com.brew.brassia.distribution.adapter.inbound.gateway;

import br.com.brew.brassia.distribution.CustomerDeliveryLookup;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Responde a última visita do caminhão ao cliente — entregue ou não (DUV-CRM-001). */
@Component
class JdbcCustomerDeliveryAdapter implements CustomerDeliveryLookup {

    private final JdbcClient jdbc;

    JdbcCustomerDeliveryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<LocalDate> lastDeliveryOn(UUID breweryId, UUID customerId) {
        // Qualquer desfecho conta: recusa e ausência também são relacionamento — o caminhão foi até lá.
        // Contar só o entregue faria o relógio correr para quem a cervejaria acabou de visitar.
        return jdbc.sql("""
                SELECT max(p.occurred_at)::date
                FROM distribution_proof p
                JOIN distribution_load_stop s ON s.id = p.stop_id
                JOIN distribution_load l ON l.id = s.load_id
                WHERE l.brewery_id = :brewery AND s.customer_id = :customer
                """)
                .param("brewery", breweryId).param("customer", customerId)
                .query(LocalDate.class).optional();
    }
}
