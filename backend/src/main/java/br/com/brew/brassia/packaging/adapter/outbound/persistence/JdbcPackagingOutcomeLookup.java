package br.com.brew.brassia.packaging.adapter.outbound.persistence;

import br.com.brew.brassia.packaging.PackagingOutcomeLookup;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * O que cada envase executado do lote planejou e entregou (CST-002).
 *
 * <p>{@code JOIN} e não {@code LEFT JOIN}: plano sem execução fica de fora porque não há real a
 * comparar. Que o lote não foi envasado é assunto de quem monta o relatório, e ele sabe disso pela
 * lista vazia.
 */
@Component
class JdbcPackagingOutcomeLookup implements PackagingOutcomeLookup {

    private final JdbcClient jdbc;

    JdbcPackagingOutcomeLookup(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<PackagingOutcome> outcomesOfBatch(UUID breweryId, UUID batchId) {
        return jdbc.sql("""
                SELECT p.code, p.planned_volume_liters, r.packaged_volume_liters,
                       r.rejected_volume_liters, r.losses_liters
                FROM packaging_run r
                JOIN packaging_plan p ON p.id = r.plan_id AND p.brewery_id = r.brewery_id
                WHERE r.brewery_id = :brewery AND r.batch_id = :batch
                ORDER BY r.executed_at
                """)
                .param("brewery", breweryId).param("batch", batchId)
                .query((rs, rowNum) -> new PackagingOutcome(rs.getString("code"),
                        rs.getBigDecimal("planned_volume_liters"),
                        rs.getBigDecimal("packaged_volume_liters"),
                        rs.getBigDecimal("rejected_volume_liters"), rs.getBigDecimal("losses_liters")))
                .list();
    }
}
