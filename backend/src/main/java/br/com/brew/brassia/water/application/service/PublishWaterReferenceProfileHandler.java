package br.com.brew.brassia.water.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.water.application.port.inbound.PublishWaterReferenceProfileUseCase;
import br.com.brew.brassia.water.application.port.outbound.WaterReferenceProfileRepository;
import java.util.Map;
import java.util.Objects;

public final class PublishWaterReferenceProfileHandler implements PublishWaterReferenceProfileUseCase {

    private final WaterReferenceProfileRepository profiles;
    private final AuditTrail audit;

    public PublishWaterReferenceProfileHandler(WaterReferenceProfileRepository profiles, AuditTrail audit) {
        this.profiles = Objects.requireNonNull(profiles);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        var profile = profiles.findVisible(command.breweryId(), command.id())
                .orElseThrow(() -> new IllegalArgumentException("perfil de referência inexistente ou fora do escopo"));

        profile.publish(); // IllegalStateException = 409 se já publicado
        if (!profiles.markPublished(profile.id().value(), profile.version())) {
            throw new IllegalStateException("perfil não está em rascunho ou foi alterado concorrentemente");
        }

        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "water.reference_profile.publish",
                "water_reference_profile", profile.id().value().toString(), Map.of("name", profile.name())));

        return new Result(profile.status().name());
    }
}
