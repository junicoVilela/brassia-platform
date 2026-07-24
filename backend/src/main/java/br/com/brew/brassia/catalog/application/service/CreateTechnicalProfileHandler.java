package br.com.brew.brassia.catalog.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.catalog.application.port.inbound.CreateTechnicalProfileUseCase;
import br.com.brew.brassia.catalog.application.port.outbound.IngredientRepository;
import br.com.brew.brassia.catalog.application.port.outbound.TechnicalProfileRepository;
import br.com.brew.brassia.catalog.domain.IngredientTechnicalProfile;
import java.util.Map;
import java.util.Objects;

public final class CreateTechnicalProfileHandler implements CreateTechnicalProfileUseCase {

    private final IngredientRepository ingredients;
    private final TechnicalProfileRepository profiles;
    private final AuditTrail audit;

    public CreateTechnicalProfileHandler(IngredientRepository ingredients, TechnicalProfileRepository profiles,
            AuditTrail audit) {
        this.ingredients = Objects.requireNonNull(ingredients);
        this.profiles = Objects.requireNonNull(profiles);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result handle(Command command) {
        ingredients.findById(command.breweryId(), command.ingredientId())
                .orElseThrow(() -> new IllegalArgumentException("ingrediente inexistente"));
        if (profiles.findByIngredient(command.breweryId(), command.ingredientId()).isPresent()) {
            throw new IllegalStateException("ingrediente já possui perfil técnico");
        }

        var profile = IngredientTechnicalProfile.draft(command.breweryId(), command.ingredientId(),
                command.manufacturer(), command.origin(), command.form(), command.purpose(), command.laboratory(),
                command.labCode(), command.ranges(), command.descriptors(), command.sourceId(), command.sourceName());
        profiles.insert(profile);

        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "catalog.technical_profile.create",
                "ingredient_technical_profile", profile.id().value().toString(),
                Map.of("ingredient", command.ingredientId().toString(),
                        "ranges", Integer.toString(profile.ranges().size()))));

        return new Result(profile.id().value(), profile.status().name());
    }
}
