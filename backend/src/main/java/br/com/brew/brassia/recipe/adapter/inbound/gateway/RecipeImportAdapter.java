package br.com.brew.brassia.recipe.adapter.inbound.gateway;

import br.com.brew.brassia.recipe.RecipeImportCommands;
import br.com.brew.brassia.recipe.application.port.inbound.CreateRecipeUseCase;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * A porta publicada de importação, sobre o caso de uso que já existe (COM-003).
 *
 * <p><strong>Delega, e não reimplementa.</strong> Um caminho paralelo de criação seria um segundo lugar
 * onde as mesmas validações — volume contra capacidade, percentuais de mostura, nome obrigatório —
 * precisariam ser mantidas iguais, e elas divergiriam na primeira mudança. É a mesma razão que fez a
 * abertura de não conformidade pela IA passar pelo caso de uso da tela (DEC-AIA-001).
 *
 * <p>Sem alvos: o retrato público traz as métricas <em>calculadas</em> do autor, e copiá-las como
 * <em>alvo</em> da receita nova afirmaria que quem copiou quer exatamente aquilo. Ele quer a receita;
 * os alvos dele são dele.
 */
@Component
class RecipeImportAdapter implements RecipeImportCommands {

    private final CreateRecipeUseCase createRecipe;

    RecipeImportAdapter(CreateRecipeUseCase createRecipe) {
        this.createRecipe = Objects.requireNonNull(createRecipe);
    }

    @Override
    public UUID importRecipe(UUID breweryId, UUID actorId, ImportedRecipe recipe) {
        var items = recipe.items().stream()
                .map(i -> new CreateRecipeUseCase.ItemInput(i.ingredientId(), i.stage(), i.quantity(),
                        i.unit(), i.timingMinutes(), null))
                .toList();
        return createRecipe.handle(new CreateRecipeUseCase.Command(actorId, breweryId, recipe.name(),
                recipe.equipmentId(), recipe.batchVolumeLiters(), null, null, null, null,
                recipe.boilTimeMinutes(), null, null, items)).id();
    }
}
