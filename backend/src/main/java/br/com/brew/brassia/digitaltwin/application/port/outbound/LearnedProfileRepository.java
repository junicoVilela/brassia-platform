package br.com.brew.brassia.digitaltwin.application.port.outbound;

import br.com.brew.brassia.digitaltwin.domain.LearnedProfile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistência dos perfis aprendidos (DTW-001). */
public interface LearnedProfileRepository {

    void insert(LearnedProfile profile);

    /**
     * A maior versão já calculada para a receita.
     *
     * <p>A versão é derivada daqui, não informada por quem chama: deixar o cliente escolher abriria a
     * porta para duas "versão 3" do mesmo perfil, e a referência a uma versão deixaria de identificar qual
     * cálculo produziu o número.
     */
    int highestVersionOf(UUID breweryId, UUID recipeId);

    Optional<LearnedProfile> latestOf(UUID breweryId, UUID recipeId);

    /** O histórico de versões — é onde se vê o perfil mudar conforme a operação muda. */
    List<LearnedProfile> historyOf(UUID breweryId, UUID recipeId);
}
