package br.com.brew.brassia.community.application.service;

import br.com.brew.brassia.catalog.IngredientDirectory;
import br.com.brew.brassia.community.application.port.outbound.PublishedRecipeRepository;
import br.com.brew.brassia.community.application.port.outbound.RecipeForkRepository;
import br.com.brew.brassia.community.domain.ForkNotAllowedException;
import br.com.brew.brassia.community.domain.ForkOrigin;
import br.com.brew.brassia.community.domain.PublishedRecipe;
import br.com.brew.brassia.community.domain.UnknownPublicationException;
import br.com.brew.brassia.community.domain.UnmappedIngredientsException;
import br.com.brew.brassia.recipe.RecipeImportCommands;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;

/**
 * Copiar uma receita publicada (COM-003).
 *
 * <p><strong>A cópia é feita do retrato congelado, e nunca da receita do autor.</strong> É o que dá
 * sentido a "sem acesso futuro ao conteúdo privado": o forkador leva o que estava publicado naquele
 * momento — nem mais, nem o que vier depois.
 */
public class ForkHandlers {

    private final PublishedRecipeRepository library;
    private final RecipeForkRepository forks;
    private final RecipeImportCommands recipes;
    private final IngredientDirectory ingredients;

    public ForkHandlers(PublishedRecipeRepository library, RecipeForkRepository forks,
            RecipeImportCommands recipes, IngredientDirectory ingredients) {
        this.library = Objects.requireNonNull(library);
        this.forks = Objects.requireNonNull(forks);
        this.recipes = Objects.requireNonNull(recipes);
        this.ingredients = Objects.requireNonNull(ingredients);
    }

    /**
     * @param equipmentId equipamento de quem copia — o do autor nunca saiu no retrato, então a escolha
     *                    é de quem vai brassar
     */
    @org.springframework.transaction.annotation.Transactional
    public Result fork(UUID breweryId, UUID actorId, UUID publicationId, String newName,
            UUID equipmentId) {
        var publication = library.findForReader(publicationId, breweryId)
                .orElseThrow(() -> new UnknownPublicationException(publicationId));
        requireForkable(publication);

        var snapshot = publication.snapshot();
        // Cada nome precisa achar um ingrediente DESTA casa. Recusar inteiro quando falta é a decisão:
        // uma receita a que faltam três de oito ingredientes não é incompleta, é errada.
        var faltando = new ArrayList<String>();
        var itens = new ArrayList<RecipeImportCommands.ImportedItem>();
        for (var item : snapshot.items()) {
            var id = ingredients.findIdByName(breweryId, item.ingredientName());
            if (id.isEmpty()) {
                faltando.add(item.ingredientName());
                continue;
            }
            itens.add(new RecipeImportCommands.ImportedItem(id.get(), item.stage(), item.quantity(),
                    item.unit(), item.timingMinutes()));
        }
        if (!faltando.isEmpty()) {
            throw new UnmappedIngredientsException(faltando);
        }

        // Sem nome informado, a cópia NÃO reusa o do original: nome de receita é único por cervejaria,
        // e — mais importante que a colisão — duas receitas com o mesmo nome no mesmo catálogo fazem o
        // cervejeiro pegar a errada no dia da brassa. O sufixo diz o que a receita é.
        var nome = newName == null || newName.isBlank()
                ? snapshot.name() + " (cópia)"
                : newName.strip();
        var recipeId = recipes.importRecipe(breweryId, actorId, new RecipeImportCommands.ImportedRecipe(
                nome, equipmentId, snapshot.batchVolumeLiters(), snapshot.boilTimeMinutes(), itens));

        // A linhagem é gravada com os valores de AGORA, e não com referências: é o que faz a atribuição
        // sobreviver ao autor renomear, fechar ou despublicar.
        var origin = new ForkOrigin(publication.id(), publication.authorDisplayName(),
                publication.title(), publication.license(), publication.recipeVersion(), Instant.now());
        forks.insert(UUID.randomUUID(), breweryId, recipeId, origin, actorId);
        return new Result(recipeId, origin);
    }

    private void requireForkable(PublishedRecipe publication) {
        if (!publication.readableByOutsider()) {
            // Não se forka o que não se pode ler — e isto não é sobre licença, é a matriz de novo.
            throw ForkNotAllowedException.unreachable();
        }
        if (!publication.license().allowsDerivatives()) {
            throw ForkNotAllowedException.license(publication.license());
        }
    }

    public record Result(UUID recipeId, ForkOrigin origin) {}
}
