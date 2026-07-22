package br.com.brew.brassia.security.application.port.outbound;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccessReviewRepository {
    record ReviewView(UUID id, String name, String status, UUID reviewerId, Instant dueAt) {}
    record ItemView(UUID id, UUID userId, UUID groupId, String decision) {}

    UUID create(UUID breweryId, String name, UUID reviewerId, Instant dueAt);
    Optional<ReviewView> findById(UUID id);
    List<ReviewView> listByBrewery(UUID breweryId);
    void addItem(UUID reviewId, UUID userId, UUID groupId);
    List<ItemView> listItems(UUID reviewId);
    Optional<ItemView> findItem(UUID itemId);
    void decideItem(UUID itemId, String decision, String justification);
    void complete(UUID reviewId);
}
