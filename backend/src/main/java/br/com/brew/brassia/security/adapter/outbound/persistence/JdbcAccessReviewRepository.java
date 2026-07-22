package br.com.brew.brassia.security.adapter.outbound.persistence;

import br.com.brew.brassia.security.application.port.outbound.AccessReviewRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAccessReviewRepository implements AccessReviewRepository {
    private final JdbcClient jdbc;

    JdbcAccessReviewRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UUID create(UUID breweryId, String name, UUID reviewerId, Instant dueAt) {
        var id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO access_review (id, brewery_id, name, status, reviewer_id, due_at)
                VALUES (:id, :breweryId, :name, 'OPEN', :reviewerId, :dueAt)
                """)
                .param("id", id)
                .param("breweryId", breweryId)
                .param("name", name)
                .param("reviewerId", reviewerId)
                .param("dueAt", Timestamp.from(dueAt))
                .update();
        return id;
    }

    @Override
    public Optional<ReviewView> findById(UUID id) {
        return jdbc.sql("""
                SELECT id, name, status, reviewer_id, due_at FROM access_review WHERE id = :id
                """)
                .param("id", id)
                .query((rs, n) -> new ReviewView(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getString("status"),
                        rs.getObject("reviewer_id", UUID.class),
                        rs.getTimestamp("due_at").toInstant()))
                .optional();
    }

    @Override
    public List<ReviewView> listByBrewery(UUID breweryId) {
        return jdbc.sql("""
                SELECT id, name, status, reviewer_id, due_at FROM access_review
                WHERE brewery_id = :breweryId ORDER BY created_at DESC
                """)
                .param("breweryId", breweryId)
                .query((rs, n) -> new ReviewView(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getString("status"),
                        rs.getObject("reviewer_id", UUID.class),
                        rs.getTimestamp("due_at").toInstant()))
                .list();
    }

    @Override
    public void addItem(UUID reviewId, UUID userId, UUID groupId) {
        jdbc.sql("""
                INSERT INTO access_review_item (id, review_id, user_id, group_id)
                VALUES (:id, :reviewId, :userId, :groupId)
                """)
                .param("id", UUID.randomUUID())
                .param("reviewId", reviewId)
                .param("userId", userId)
                .param("groupId", groupId)
                .update();
    }

    @Override
    public List<ItemView> listItems(UUID reviewId) {
        return jdbc.sql("""
                SELECT id, user_id, group_id, decision FROM access_review_item WHERE review_id = :reviewId
                """)
                .param("reviewId", reviewId)
                .query((rs, n) -> new ItemView(
                        rs.getObject("id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getObject("group_id", UUID.class),
                        rs.getString("decision")))
                .list();
    }

    @Override
    public Optional<ItemView> findItem(UUID itemId) {
        return jdbc.sql("""
                SELECT id, user_id, group_id, decision FROM access_review_item WHERE id = :id
                """)
                .param("id", itemId)
                .query((rs, n) -> new ItemView(
                        rs.getObject("id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getObject("group_id", UUID.class),
                        rs.getString("decision")))
                .optional();
    }

    @Override
    public void decideItem(UUID itemId, String decision, String justification) {
        jdbc.sql("""
                UPDATE access_review_item SET decision = :decision, justification = :justification, decided_at = now()
                WHERE id = :id AND decision IS NULL
                """)
                .param("decision", decision)
                .param("justification", justification)
                .param("id", itemId)
                .update();
    }

    @Override
    public void complete(UUID reviewId) {
        jdbc.sql("UPDATE access_review SET status = 'COMPLETED' WHERE id = :id")
                .param("id", reviewId)
                .update();
    }
}
