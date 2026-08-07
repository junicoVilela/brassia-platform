package br.com.brew.brassia.production.adapter.outbound.persistence;

import br.com.brew.brassia.production.BatchOutcomeLookup;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Volume planejado, volume transferido e perda do lote (CST-002).
 *
 * <p>O {@code LEFT JOIN} é a decisão: lote sem transferência responde com o planejado e o real
 * vazio, em vez de sumir da consulta. Sumir faria o relatório de variação de um lote que ainda
 * está fervendo parecer um relatório sem desvios.
 */
@Component
class JdbcBatchOutcomeLookup implements BatchOutcomeLookup {

    private final JdbcClient jdbc;

    JdbcBatchOutcomeLookup(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<BatchOutcome> outcomeOf(UUID breweryId, UUID batchId) {
        return jdbc.sql("""
                SELECT b.volume_liters AS planned, t.volume_liters AS transferred, t.losses_liters
                FROM production_batch b
                LEFT JOIN production_transfer t ON t.batch_id = b.id AND t.brewery_id = b.brewery_id
                WHERE b.brewery_id = :brewery AND b.id = :batch
                """)
                .param("brewery", breweryId).param("batch", batchId)
                .query((rs, rowNum) -> new BatchOutcome(rs.getBigDecimal("planned"),
                        rs.getBigDecimal("transferred"), rs.getBigDecimal("losses_liters")))
                .optional();
    }
}
