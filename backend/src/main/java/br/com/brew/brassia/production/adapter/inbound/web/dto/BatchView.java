package br.com.brew.brassia.production.adapter.inbound.web.dto;

import br.com.brew.brassia.production.domain.Batch;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BatchView(
        UUID id, UUID orderId, String code, UUID recipeId, int recipeVersion, String recipeName,
        BigDecimal volumeLiters, String status, Instant startedAt, List<BatchStepView> steps) {

    public static BatchView from(Batch b) {
        return new BatchView(b.id().value(), b.orderId(), b.code(), b.recipeId(), b.recipeVersion(),
                b.recipeName(), b.volumeLiters(), b.status().name(), b.startedAt(),
                b.steps().stream().map(BatchStepView::from).toList());
    }

    public record BatchStepView(UUID id, int sequence, String type, String label, String status, Instant startedAt,
            Instant completedAt) {
        static BatchStepView from(br.com.brew.brassia.production.domain.BatchStep s) {
            return new BatchStepView(s.id(), s.sequence(), s.type().name(), s.label(), s.status().name(),
                    s.startedAt(), s.completedAt());
        }
    }
}
