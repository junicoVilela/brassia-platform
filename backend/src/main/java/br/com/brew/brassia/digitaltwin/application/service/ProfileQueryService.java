package br.com.brew.brassia.digitaltwin.application.service;

import br.com.brew.brassia.digitaltwin.application.port.inbound.ProfileQueries;
import br.com.brew.brassia.digitaltwin.application.port.outbound.LearnedProfileRepository;
import br.com.brew.brassia.digitaltwin.domain.LearnedProfile;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Consultas do perfil aprendido (DTW-001). */
public final class ProfileQueryService implements ProfileQueries {

    private final LearnedProfileRepository profiles;

    public ProfileQueryService(LearnedProfileRepository profiles) {
        this.profiles = Objects.requireNonNull(profiles, "profiles");
    }

    @Override
    public Optional<LearnedProfile> latest(UUID breweryId, UUID recipeId) {
        return profiles.latestOf(Objects.requireNonNull(breweryId, "breweryId"),
                Objects.requireNonNull(recipeId, "recipeId"));
    }

    @Override
    public List<LearnedProfile> history(UUID breweryId, UUID recipeId) {
        return profiles.historyOf(Objects.requireNonNull(breweryId, "breweryId"),
                Objects.requireNonNull(recipeId, "recipeId"));
    }
}
