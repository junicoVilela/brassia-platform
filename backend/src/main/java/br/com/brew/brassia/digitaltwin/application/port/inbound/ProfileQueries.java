package br.com.brew.brassia.digitaltwin.application.port.inbound;

import br.com.brew.brassia.digitaltwin.domain.LearnedProfile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Consulta dos perfis aprendidos (DTW-001). */
public interface ProfileQueries {

    Optional<LearnedProfile> latest(UUID breweryId, UUID recipeId);

    List<LearnedProfile> history(UUID breweryId, UUID recipeId);
}
