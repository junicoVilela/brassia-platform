package br.com.brew.brassia.security.application.port.inbound;

import br.com.brew.brassia.security.application.port.outbound.AccessReviewRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ManageAccessReviewUseCase {
    UUID createReview(CreateReviewCommand command);
    void decideItem(DecideItemCommand command);
    List<AccessReviewRepository.ReviewView> listReviews(UUID breweryId);
    List<AccessReviewRepository.ItemView> listItems(UUID reviewId);

    record CreateReviewCommand(UUID breweryId, String name, UUID reviewerId, Instant dueAt) {}
    record DecideItemCommand(UUID breweryId, UUID reviewerId, UUID itemId, String decision, String justification) {}
}
