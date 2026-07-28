package br.com.brew.brassia.fermentation.application.service;

import br.com.brew.brassia.fermentation.application.port.inbound.ListProfilesUseCase;
import br.com.brew.brassia.fermentation.application.port.outbound.ProfileRepository;
import br.com.brew.brassia.fermentation.domain.FermentationProfile;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ListProfilesHandler implements ListProfilesUseCase {

    private final ProfileRepository repository;

    public ListProfilesHandler(ProfileRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Override
    public List<FermentationProfile> handle(UUID breweryId) {
        return repository.findAll(breweryId);
    }
}
