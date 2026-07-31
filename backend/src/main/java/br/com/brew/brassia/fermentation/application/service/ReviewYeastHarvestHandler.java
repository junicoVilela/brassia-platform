package br.com.brew.brassia.fermentation.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.fermentation.application.port.inbound.ReviewYeastHarvestUseCase;
import br.com.brew.brassia.fermentation.application.port.outbound.YeastHarvestRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Aprova ou reprova uma coleta de levedura (YST-001). A decisão é humana e terminal; a
 * reprovação exige motivo e tira a coleta de circulação em definitivo.
 */
public final class ReviewYeastHarvestHandler implements ReviewYeastHarvestUseCase {

    private final YeastHarvestRepository harvests;
    private final AuditTrail audit;

    public ReviewYeastHarvestHandler(YeastHarvestRepository harvests, AuditTrail audit) {
        this.harvests = Objects.requireNonNull(harvests);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public void handle(Command command) {
        var harvest = harvests.findById(command.breweryId(), command.harvestId())
                .orElseThrow(() -> new IllegalArgumentException("coleta inexistente"));

        var at = Instant.now();
        if (command.approve()) {
            harvest.approve(command.actorId(), command.note(), at);
        } else {
            harvest.reject(command.actorId(), command.note(), at);
        }
        harvests.updateReview(harvest);

        var metadata = new HashMap<String, String>();
        metadata.put("code", harvest.code());
        metadata.put("status", harvest.status().name());
        if (harvest.reviewNote() != null) {
            metadata.put("note", harvest.reviewNote());
        }
        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "fermentation.yeast.review",
                "fermentation.yeast.harvest", harvest.id().toString(), Map.copyOf(metadata)));
    }
}
