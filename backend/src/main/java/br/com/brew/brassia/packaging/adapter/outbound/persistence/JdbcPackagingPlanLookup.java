package br.com.brew.brassia.packaging.adapter.outbound.persistence;

import br.com.brew.brassia.packaging.PackagingPlanLookup;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class JdbcPackagingPlanLookup implements PackagingPlanLookup {

    private final JdbcClient jdbc;

    JdbcPackagingPlanLookup(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<UUID> plansOfBatch(UUID breweryId, UUID batchId) {
        return jdbc.sql("SELECT id FROM packaging_plan WHERE brewery_id = :brewery AND batch_id = :batch "
                        + "ORDER BY planned_start")
                .param("brewery", breweryId).param("batch", batchId)
                .query(UUID.class).list();
    }
}
