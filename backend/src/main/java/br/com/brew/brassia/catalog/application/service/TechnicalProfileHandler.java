package br.com.brew.brassia.catalog.application.service;

import br.com.brew.brassia.catalog.application.port.inbound.TechnicalProfileUseCase;
import br.com.brew.brassia.catalog.application.port.outbound.TechnicalProfileRepository;
import br.com.brew.brassia.catalog.domain.IngredientTechnicalProfile;
import java.util.Objects;
import java.util.Optional;

public final class TechnicalProfileHandler implements TechnicalProfileUseCase {

    private final TechnicalProfileRepository profiles;

    public TechnicalProfileHandler(TechnicalProfileRepository profiles) {
        this.profiles = Objects.requireNonNull(profiles);
    }

    @Override
    public Optional<ProfileView> handle(Query query) {
        return profiles.findByIngredient(query.breweryId(), query.ingredientId()).map(TechnicalProfileHandler::toView);
    }

    private static ProfileView toView(IngredientTechnicalProfile p) {
        return new ProfileView(p.ingredientId(), p.manufacturer(), p.origin(), p.form(), p.purpose(), p.laboratory(),
                p.labCode(), p.ranges(), p.descriptors(), p.sourceId(), p.sourceName(), p.status().name());
    }
}
