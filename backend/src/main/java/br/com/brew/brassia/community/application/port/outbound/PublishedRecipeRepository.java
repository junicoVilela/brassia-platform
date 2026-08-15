package br.com.brew.brassia.community.application.port.outbound;

import br.com.brew.brassia.community.domain.PublishedRecipe;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistência da biblioteca (COM-001).
 *
 * <p><strong>Há duas buscas, e a separação é a fronteira.</strong> {@link #findForReader} responde a
 * pergunta de leitura — e recebe quem pergunta, para decidir se pode ver. {@link #listPublic} nunca
 * recebe cervejaria porque a biblioteca pública não é de ninguém: filtrar por inquilino ali seria mostrar
 * a cada um só o que ele mesmo publicou, que é o oposto de biblioteca.
 */
public interface PublishedRecipeRepository {

    void insert(PublishedRecipe published);

    void update(PublishedRecipe published);

    /** Sem checagem de visibilidade: é a busca de quem administra a própria publicação. */
    Optional<PublishedRecipe> findOwned(UUID breweryId, UUID id);

    /**
     * A publicação como {@code readerBreweryId} pode vê-la.
     *
     * <p>Vazio quando não pode — e vazio, e não erro: quem não pode ver não deve nem saber que existe.
     */
    Optional<PublishedRecipe> findForReader(UUID id, UUID readerBreweryId);

    /** A vitrine: só o que está no ar e é público. */
    List<PublishedRecipe> listPublic(int limit);

    /** O que esta cervejaria publicou, em qualquer visibilidade — a estante do autor. */
    List<PublishedRecipe> listOwned(UUID breweryId);

    boolean versionAlreadyPublished(UUID recipeId, long recipeVersion);
}
