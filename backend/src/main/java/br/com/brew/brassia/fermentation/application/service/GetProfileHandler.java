package br.com.brew.brassia.fermentation.application.service;

import br.com.brew.brassia.fermentation.application.port.inbound.GetProfileUseCase;
import br.com.brew.brassia.fermentation.application.port.outbound.ProfileRepository;
import br.com.brew.brassia.fermentation.domain.FermentationProfile;
import java.util.Objects;
import java.util.UUID;

public final class GetProfileHandler implements GetProfileUseCase {

    private final ProfileRepository repository;

    public GetProfileHandler(ProfileRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Override
    public FermentationProfile handle(UUID breweryId, UUID profileId) {
        return repository.findById(breweryId, profileId)
                .orElseThrow(() -> new IllegalArgumentException("perfil inexistente"));
    }
}
