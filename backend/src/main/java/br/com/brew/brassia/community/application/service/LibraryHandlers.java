package br.com.brew.brassia.community.application.service;

import br.com.brew.brassia.catalog.IngredientSpecLookup;
import br.com.brew.brassia.community.application.port.inbound.LibraryCommands;
import br.com.brew.brassia.community.application.port.outbound.PublishedRecipeRepository;
import br.com.brew.brassia.community.domain.AlreadyPublishedException;
import br.com.brew.brassia.community.domain.PublicRecipeSnapshot;
import br.com.brew.brassia.community.domain.PublishedRecipe;
import br.com.brew.brassia.community.domain.RecipeLicense;
import br.com.brew.brassia.community.domain.UnknownPublicationException;
import br.com.brew.brassia.community.domain.Visibility;
import br.com.brew.brassia.recipe.RecipeLookup;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso da biblioteca (COM-001).
 *
 * <p><strong>O retrato público é montado aqui, campo a campo.</strong> É o único lugar do sistema onde
 * dado de receita atravessa a fronteira para fora da cervejaria, e por isso a construção é explícita: um
 * campo novo na {@code Recipe} não passa a sair porque alguém acrescentou — ele só sai quando alguém
 * escrever aqui que deve sair.
 */
public class LibraryHandlers implements LibraryCommands {

    private final PublishedRecipeRepository library;
    private final RecipeLookup recipes;
    private final IngredientSpecLookup ingredients;

    public LibraryHandlers(PublishedRecipeRepository library, RecipeLookup recipes,
            IngredientSpecLookup ingredients) {
        this.library = Objects.requireNonNull(library);
        this.recipes = Objects.requireNonNull(recipes);
        this.ingredients = Objects.requireNonNull(ingredients);
    }

    @Override
    @Transactional
    public UUID publish(UUID breweryId, UUID actorId, String actorName, UUID recipeId, String title,
            String summary, RecipeLicense license, Visibility visibility) {
        // Só receita PUBLICADA vai para a biblioteca: um rascunho é trabalho em andamento, e publicá-lo
        // colocaria lá fora uma versão que a própria casa ainda não considera pronta.
        var cabecalho = recipes.findPublishedForOrder(breweryId, recipeId)
                .orElseThrow(() -> new UnknownPublicationException(recipeId));
        var composicao = recipes.findPublishedComposition(breweryId, recipeId)
                .orElseThrow(() -> new UnknownPublicationException(recipeId));

        if (library.versionAlreadyPublished(recipeId, cabecalho.version())) {
            throw new AlreadyPublishedException(cabecalho.version());
        }

        var snapshot = retrato(breweryId, cabecalho, composicao);
        var publicacao = PublishedRecipe.publish(UUID.randomUUID(), breweryId, recipeId,
                cabecalho.version(), actorId, actorName, title, summary, license, visibility, snapshot,
                Instant.now());
        library.insert(publicacao);
        return publicacao.id();
    }

    /**
     * A allowlist em ação.
     *
     * <p>Cada campo que sai está escrito abaixo. O que a receita tem e não aparece aqui — cervejaria,
     * equipamento, identificador de ingrediente, linhagem interna — não sai, e não sai <em>por
     * construção</em>, e não por alguém ter lembrado de remover.
     */
    private PublicRecipeSnapshot retrato(UUID breweryId, RecipeLookup.PublishedForOrder cabecalho,
            RecipeLookup.PublishedComposition composicao) {
        var itens = new ArrayList<PublicRecipeSnapshot.Item>();
        for (var item : composicao.items()) {
            // O NOME do ingrediente, e nunca o identificador: ele é a chave do catálogo, onde moram
            // preço de compra e fornecedor.
            var nome = ingredients.find(breweryId, item.ingredientId())
                    .map(IngredientSpecLookup.Spec::name)
                    .orElse("Ingrediente");
            itens.add(new PublicRecipeSnapshot.Item(nome, item.stage(), item.quantity(), item.unit(),
                    null, null));
        }

        var metricas = cabecalho.metrics()
                .map(m -> new PublicRecipeSnapshot.Targets(m.ogSg(), m.fgSg(), m.ibu(), m.colorEbc(),
                        m.abv()))
                .orElse(null);

        return new PublicRecipeSnapshot(cabecalho.name(), null, composicao.batchVolumeLiters(), null,
                metricas, itens);
    }

    @Override
    @Transactional
    public void changeVisibility(UUID breweryId, UUID actorId, UUID publicationId, Visibility visibility) {
        var p = owned(breweryId, publicationId);
        p.changeVisibility(visibility);
        library.update(p);
    }

    @Override
    @Transactional
    public void relicense(UUID breweryId, UUID actorId, UUID publicationId, RecipeLicense license) {
        var p = owned(breweryId, publicationId);
        p.relicense(license);
        library.update(p);
    }

    @Override
    @Transactional
    public void unpublish(UUID breweryId, UUID actorId, UUID publicationId) {
        var p = owned(breweryId, publicationId);
        p.unpublish(Instant.now());
        library.update(p);
    }

    /** Administrar a própria publicação exige ser dono dela — e a busca já filtra por cervejaria. */
    private PublishedRecipe owned(UUID breweryId, UUID publicationId) {
        return library.findOwned(breweryId, publicationId)
                .orElseThrow(() -> new UnknownPublicationException(publicationId));
    }
}
