package br.com.brew.brassia.community.application.port.inbound;

import br.com.brew.brassia.community.domain.RecipeLicense;
import br.com.brew.brassia.community.domain.Visibility;
import java.util.UUID;

/** O que se faz com a biblioteca (COM-001). */
public interface LibraryCommands {

    /**
     * Publica a versão publicada da receita.
     *
     * <p>O retrato é montado aqui, por allowlist, e congelado. Republicar a mesma versão é recusado: duas
     * entradas da mesma versão concorreriam na busca com títulos possivelmente diferentes, e ninguém
     * saberia qual é a boa.
     */
    UUID publish(UUID breweryId, UUID actorId, String actorName, UUID recipeId, String title,
            String summary, RecipeLicense license, Visibility visibility);

    void changeVisibility(UUID breweryId, UUID actorId, UUID publicationId, Visibility visibility);

    void relicense(UUID breweryId, UUID actorId, UUID publicationId, RecipeLicense license);

    void unpublish(UUID breweryId, UUID actorId, UUID publicationId);
}
