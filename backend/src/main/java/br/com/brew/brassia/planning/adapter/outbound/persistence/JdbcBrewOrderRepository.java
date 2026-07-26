package br.com.brew.brassia.planning.adapter.outbound.persistence;

import br.com.brew.brassia.planning.application.port.outbound.BrewOrderRepository;
import br.com.brew.brassia.planning.domain.BrewOrder;
import br.com.brew.brassia.planning.domain.BrewOrderId;
import br.com.brew.brassia.planning.domain.BrewOrderStatus;
import br.com.brew.brassia.planning.domain.OrderSnapshot;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class JdbcBrewOrderRepository implements BrewOrderRepository {

    private static final String COLUMNS = """
            SELECT id, brewery_id, code, recipe_id, recipe_version, volume_liters, snapshot, status,
                   assigned_user_id, released_at, cancel_reason, cancelled_at, version
            FROM brew_order
            """;

    // ObjectMapper próprio: o snapshot é um record simples (BigDecimal/UUID/int/String),
    // que o Jackson serializa sem módulos extras; evita depender de um bean compartilhado.
    private static final ObjectMapper JSON = new ObjectMapper();

    private final JdbcClient jdbc;

    JdbcBrewOrderRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long nextSequence(UUID breweryId, int year) {
        return jdbc.sql("""
                INSERT INTO brew_order_sequence (brewery_id, year, next_val) VALUES (:brewery, :year, 1)
                ON CONFLICT (brewery_id, year) DO UPDATE SET next_val = brew_order_sequence.next_val + 1
                RETURNING next_val
                """)
                .param("brewery", breweryId).param("year", year)
                .query(Long.class).single();
    }

    @Override
    public void insert(BrewOrder o) {
        jdbc.sql("""
                INSERT INTO brew_order (
                    id, brewery_id, code, recipe_id, recipe_version, volume_liters, snapshot, status, version,
                    created_at)
                VALUES (:id, :brewery, :code, :recipe, :recipeVersion, :volume, CAST(:snapshot AS jsonb),
                        :status, :version, :at)
                """)
                .param("id", o.id().value())
                .param("brewery", o.breweryId())
                .param("code", o.code())
                .param("recipe", o.recipeId())
                .param("recipeVersion", o.recipeVersion())
                .param("volume", o.volumeLiters())
                .param("snapshot", write(o.snapshot()))
                .param("status", o.status().name())
                .param("version", o.version())
                .param("at", Timestamp.from(Instant.now()))
                .update();
    }

    @Override
    public List<BrewOrder> findPage(UUID breweryId, int page, int size) {
        return jdbc.sql(COLUMNS + " WHERE brewery_id = :brewery ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
                .param("brewery", breweryId)
                .param("limit", size)
                .param("offset", (long) page * size)
                .query((rs, n) -> map(rs))
                .list();
    }

    @Override
    public long count(UUID breweryId) {
        return jdbc.sql("SELECT COUNT(*) FROM brew_order WHERE brewery_id = :brewery")
                .param("brewery", breweryId)
                .query(Long.class).single();
    }

    @Override
    public Optional<BrewOrder> findById(UUID breweryId, UUID id) {
        return jdbc.sql(COLUMNS + " WHERE brewery_id = :brewery AND id = :id")
                .param("brewery", breweryId).param("id", id)
                .query((rs, n) -> map(rs))
                .optional();
    }

    @Override
    public boolean markReleased(UUID breweryId, UUID id, UUID assignedUserId, Instant at) {
        int updated = jdbc.sql("""
                UPDATE brew_order
                SET status = 'RELEASED', assigned_user_id = :user, released_at = :at, version = version + 1
                WHERE brewery_id = :brewery AND id = :id AND status = 'DRAFT'
                """)
                .param("brewery", breweryId)
                .param("id", id)
                .param("user", assignedUserId)
                .param("at", Timestamp.from(at))
                .update();
        return updated > 0;
    }

    @Override
    public boolean markCancelled(UUID breweryId, UUID id, String reason, Instant at) {
        int updated = jdbc.sql("""
                UPDATE brew_order
                SET status = 'CANCELLED', cancel_reason = :reason, cancelled_at = :at, version = version + 1
                WHERE brewery_id = :brewery AND id = :id AND status IN ('DRAFT', 'RELEASED')
                """)
                .param("brewery", breweryId)
                .param("id", id)
                .param("reason", reason)
                .param("at", Timestamp.from(at))
                .update();
        return updated > 0;
    }

    private BrewOrder map(ResultSet rs) throws SQLException {
        var releasedAt = rs.getTimestamp("released_at");
        var cancelledAt = rs.getTimestamp("cancelled_at");
        return BrewOrder.reconstitute(
                new BrewOrderId(rs.getObject("id", UUID.class)),
                rs.getObject("brewery_id", UUID.class),
                rs.getString("code"),
                rs.getObject("recipe_id", UUID.class),
                rs.getInt("recipe_version"),
                rs.getBigDecimal("volume_liters"),
                read(rs.getString("snapshot")),
                BrewOrderStatus.valueOf(rs.getString("status")),
                rs.getObject("assigned_user_id", UUID.class),
                releasedAt == null ? null : releasedAt.toInstant(),
                rs.getString("cancel_reason"),
                cancelledAt == null ? null : cancelledAt.toInstant(),
                rs.getLong("version"));
    }

    private String write(OrderSnapshot snapshot) {
        try {
            return JSON.writeValueAsString(snapshot);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("falha ao serializar snapshot da OP", ex);
        }
    }

    private OrderSnapshot read(String value) {
        try {
            return JSON.readValue(value, OrderSnapshot.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("falha ao ler snapshot da OP", ex);
        }
    }
}
