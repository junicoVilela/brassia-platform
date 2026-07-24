package br.com.brew.brassia.referencedata.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.referencedata.application.port.inbound.PublishStyleSetUseCase;
import br.com.brew.brassia.referencedata.application.port.outbound.StyleSetRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public final class PublishStyleSetHandler implements PublishStyleSetUseCase {

    private final StyleSetRepository styleSets;
    private final AuditTrail audit;

    public PublishStyleSetHandler(StyleSetRepository styleSets, AuditTrail audit) {
        this.styleSets = Objects.requireNonNull(styleSets);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        var set = styleSets.findVisible(command.breweryId(), command.styleSetId())
                .orElseThrow(() -> new IllegalArgumentException("conjunto inexistente ou fora do escopo"));

        var when = Instant.now();
        set.publish(when); // gate de licença + estado (IllegalStateException = 409)
        if (!styleSets.markPublished(set.id().value(), when, set.version())) {
            throw new IllegalStateException("conjunto não está em rascunho ou foi alterado concorrentemente");
        }

        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "reference.style_set.publish",
                "style_set", set.id().value().toString(),
                Map.of("authority", set.authority().name(), "edition", set.edition())));

        return new Result(set.id().value(), set.status().name(), when);
    }
}
