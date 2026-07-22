package br.com.brew.brassia.security.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.security.application.port.inbound.ManageAccessReviewUseCase;
import br.com.brew.brassia.security.application.port.inbound.ManageSegregationUseCase;
import br.com.brew.brassia.security.application.port.outbound.AccessReviewRepository;
import br.com.brew.brassia.security.application.port.outbound.GroupMembershipRepository;
import br.com.brew.brassia.security.application.port.outbound.SegregationRuleRepository;
import br.com.brew.brassia.security.domain.UserId;
import br.com.brew.brassia.shared.security.ForbiddenException;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Revisão de acessos e segregação de funções (SEC-013). */
public final class AccessReviewHandler {
    private final AccessReviewRepository reviews;
    private final GroupMembershipRepository memberships;
    private final SegregationRuleRepository segregationRules;
    private final AuditTrail audit;

    public AccessReviewHandler(
            AccessReviewRepository reviews,
            GroupMembershipRepository memberships,
            SegregationRuleRepository segregationRules,
            AuditTrail audit) {
        this.reviews = Objects.requireNonNull(reviews);
        this.memberships = Objects.requireNonNull(memberships);
        this.segregationRules = Objects.requireNonNull(segregationRules);
        this.audit = Objects.requireNonNull(audit);
    }

    public UUID createReview(ManageAccessReviewUseCase.CreateReviewCommand command) {
        var id = reviews.create(command.breweryId(), command.name(), command.reviewerId(), command.dueAt());
        for (var m : memberships.listActiveByBrewery(command.breweryId())) {
            reviews.addItem(id, m.userId(), m.groupId());
        }
        audit.record(AuditEvent.success(command.breweryId(), command.reviewerId(), "security.access-review.create",
                "access_review", id.toString(), Map.of()));
        return id;
    }

    public void decideItem(ManageAccessReviewUseCase.DecideItemCommand command) {
        var item = reviews.findItem(command.itemId()).orElseThrow(() -> new IllegalArgumentException("item inexistente"));
        if (item.userId().equals(command.reviewerId())) {
            throw new ForbiddenException("revisor não pode decidir o próprio acesso");
        }
        reviews.decideItem(command.itemId(), command.decision(), command.justification());
        if ("REMOVE".equals(command.decision())) {
            memberships.revokeMembership(new UserId(item.userId()), item.groupId(), command.breweryId());
        }
        audit.record(AuditEvent.success(command.breweryId(), command.reviewerId(), "security.access-review.decide",
                "access_review_item", command.itemId().toString(), Map.of("decision", command.decision())));
    }

    public java.util.List<AccessReviewRepository.ReviewView> listReviews(UUID breweryId) {
        return reviews.listByBrewery(breweryId);
    }

    public java.util.List<AccessReviewRepository.ItemView> listItems(UUID reviewId) {
        return reviews.listItems(reviewId);
    }

    public UUID createRule(ManageSegregationUseCase.CreateRuleCommand command) {
        var id = segregationRules.create(command.breweryId(), command.leftPermissionCode(),
                command.rightPermissionCode(), command.reason());
        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "security.segregation.create",
                "segregation_rule", id.toString(), Map.of()));
        return id;
    }

    public java.util.List<SegregationRuleRepository.RuleView> listRules(UUID breweryId) {
        return segregationRules.listActive(breweryId);
    }
}
