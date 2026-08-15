package br.com.brew.brassia.sales.adapter.outbound.persistence;

import br.com.brew.brassia.sales.application.port.outbound.PriceRepository;
import br.com.brew.brassia.sales.domain.Money;
import br.com.brew.brassia.sales.domain.PriceEntry;
import br.com.brew.brassia.sales.domain.PriceSchedule;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcPriceRepository implements PriceRepository {

    private final JdbcClient jdbc;

    JdbcPriceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PriceSchedule load(UUID breweryId, UUID productId, UUID channelId) {
        var entries = jdbc.sql("""
                SELECT amount, currency, tax_included, valid_from, valid_to
                FROM sales_price_entry
                WHERE brewery_id = :brewery AND product_id = :product AND channel_id = :channel
                ORDER BY valid_from
                """)
                .param("brewery", breweryId).param("product", productId).param("channel", channelId)
                .query((rs, row) -> {
                    var validTo = rs.getDate("valid_to");
                    return new PriceEntry(
                            new Money(rs.getBigDecimal("amount"), rs.getString("currency")),
                            rs.getBoolean("tax_included"),
                            rs.getDate("valid_from").toLocalDate(),
                            validTo == null ? null : validTo.toLocalDate());
                })
                .list();
        return PriceSchedule.reconstitute(productId, channelId, entries);
    }

    @Override
    public void applyChange(UUID breweryId, UUID productId, UUID channelId, PriceSchedule.Change change,
            UUID actorId) {
        // O encerramento vem PRIMEIRO. Inserir o novo antes de fechar o antigo faria a restrição de
        // exclusão recusar a inserção — os dois estariam abertos no mesmo instante, que é exatamente o
        // que ela existe para impedir. A ordem aqui não é estilo: é o que faz a operação passar.
        change.closedEntry().ifPresent(closed -> jdbc.sql("""
                UPDATE sales_price_entry SET valid_to = :to
                WHERE brewery_id = :brewery AND product_id = :product AND channel_id = :channel
                  AND valid_from = :from AND valid_to IS NULL
                """)
                .param("to", Date.valueOf(closed.validTo()))
                .param("brewery", breweryId).param("product", productId).param("channel", channelId)
                .param("from", Date.valueOf(closed.validFrom()))
                .update());

        var added = change.added();
        jdbc.sql("""
                INSERT INTO sales_price_entry (id, brewery_id, product_id, channel_id, amount, currency,
                                               tax_included, valid_from, valid_to, created_by, created_at)
                VALUES (:id, :brewery, :product, :channel, :amount, :currency, :tax, :from, :to, :by, :at)
                """)
                .param("id", UUID.randomUUID())
                .param("brewery", breweryId).param("product", productId).param("channel", channelId)
                .param("amount", added.price().amount())
                .param("currency", added.price().currency())
                .param("tax", added.taxIncluded())
                .param("from", Date.valueOf(added.validFrom()))
                .param("to", added.validTo() == null ? null : Date.valueOf(added.validTo()))
                .param("by", actorId)
                .param("at", Timestamp.from(Instant.now()))
                .update();
    }
}
