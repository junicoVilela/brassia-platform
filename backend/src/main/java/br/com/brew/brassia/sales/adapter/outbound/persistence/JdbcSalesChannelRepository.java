package br.com.brew.brassia.sales.adapter.outbound.persistence;

import br.com.brew.brassia.sales.application.port.outbound.SalesChannelRepository;
import br.com.brew.brassia.sales.domain.SalesChannel;
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
class JdbcSalesChannelRepository implements SalesChannelRepository {

    private final JdbcClient jdbc;

    JdbcSalesChannelRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(SalesChannel channel, UUID actorId) {
        jdbc.sql("""
                INSERT INTO sales_channel (id, brewery_id, code, name, active, created_by, created_at)
                VALUES (:id, :brewery, :code, :name, :active, :by, :at)
                """)
                .param("id", channel.id()).param("brewery", channel.breweryId())
                .param("code", channel.code()).param("name", channel.name())
                .param("active", channel.isActive()).param("by", actorId)
                .param("at", Timestamp.from(Instant.now()))
                .update();
    }

    @Override
    public void update(SalesChannel channel) {
        jdbc.sql("""
                UPDATE sales_channel SET name = :name, active = :active
                WHERE id = :id AND brewery_id = :brewery
                """)
                .param("name", channel.name()).param("active", channel.isActive())
                .param("id", channel.id()).param("brewery", channel.breweryId())
                .update();
    }

    @Override
    public Optional<SalesChannel> find(UUID breweryId, UUID id) {
        return jdbc.sql("SELECT id, brewery_id, code, name, active FROM sales_channel "
                + "WHERE id = :id AND brewery_id = :brewery")
                .param("id", id).param("brewery", breweryId)
                .query(JdbcSalesChannelRepository::map).optional();
    }

    @Override
    public List<SalesChannel> list(UUID breweryId, boolean onlyActive) {
        return jdbc.sql("""
                SELECT id, brewery_id, code, name, active FROM sales_channel
                WHERE brewery_id = :brewery AND (CAST(:onlyActive AS boolean) = FALSE OR active = TRUE)
                ORDER BY name
                """)
                .param("brewery", breweryId).param("onlyActive", onlyActive)
                .query(JdbcSalesChannelRepository::map).list();
    }

    @Override
    public boolean codeTaken(UUID breweryId, String code) {
        return jdbc.sql("SELECT COUNT(*) FROM sales_channel WHERE brewery_id = :brewery AND code = :code")
                .param("brewery", breweryId).param("code", code)
                .query(Integer.class).single() > 0;
    }

    private static SalesChannel map(ResultSet rs, int row) throws SQLException {
        return SalesChannel.reconstitute(rs.getObject("id", UUID.class),
                rs.getObject("brewery_id", UUID.class), rs.getString("code"), rs.getString("name"),
                rs.getBoolean("active"));
    }
}
