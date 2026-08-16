package br.com.brew.brassia.community.application.port.outbound;

import br.com.brew.brassia.community.domain.ForkOrigin;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistência da linhagem de fork (COM-003). */
public interface RecipeForkRepository {

    void insert(UUID id, UUID breweryId, UUID recipeId, ForkOrigin origin, UUID forkedBy);

    /** De onde veio esta receita, se veio de algum lugar. */
    Optional<ForkOrigin> originOf(UUID breweryId, UUID recipeId);

    /** Quantas cópias esta publicação gerou — a pergunta do autor. */
    int countForksOf(UUID publicationId);

    List<ForkOrigin> listOwnForks(UUID breweryId);
}
