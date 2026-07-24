package br.com.brew.brassia.catalog.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.catalog.application.port.inbound.PublishTechnicalProfileUseCase;
import br.com.brew.brassia.catalog.application.port.outbound.TechnicalProfileRepository;
import java.util.Map;
import java.util.Objects;

public final class PublishTechnicalProfileHandler implements PublishTechnicalProfileUseCase {

    private final TechnicalProfileRepository profiles;
    private final AuditTrail audit;

    public PublishTechnicalProfileHandler(TechnicalProfileRepository profiles, AuditTrail audit) {
        this.profiles = Objects.requireNonNull(profiles);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        var profile = profiles.findByIngredient(command.breweryId(), command.ingredientId())
                .orElseThrow(() -> new IllegalArgumentException("perfil técnico inexistente"));

        profile.publish(); // DRAFT → PUBLISHED (IllegalStateException = 409 se já publicado)
        if (!profiles.markPublished(command.breweryId(), command.ingredientId(), profile.version())) {
            throw new IllegalStateException("perfil não está em rascunho ou foi alterado concorrentemente");
        }

        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "catalog.technical_profile.publish",
                "ingredient_technical_profile", profile.id().value().toString(),
                Map.of("ingredient", command.ingredientId().toString())));

        return new Result(profile.status().name());
    }
}
