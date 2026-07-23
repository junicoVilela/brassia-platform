package br.com.brew.brassia.recipe.application.service;

import br.com.brew.brassia.recipe.application.port.inbound.ListRecipesUseCase;
import br.com.brew.brassia.recipe.application.port.outbound.RecipeRepository;
import br.com.brew.brassia.recipe.domain.Recipe;
import java.util.Objects;

public final class ListRecipesHandler implements ListRecipesUseCase {
    private final RecipeRepository repository;

    public ListRecipesHandler(RecipeRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Override
    public Result handle(Query query) {
        var content = repository.findPage(query.breweryId(), query.page(), query.size())
                .stream().map(ListRecipesHandler::toSummary).toList();
        var total = repository.count(query.breweryId());
        var totalPages = query.size() == 0 ? 0 : (int) Math.ceil((double) total / query.size());
        return new Result(content, query.page(), query.size(), total, totalPages);
    }

    private static Summary toSummary(Recipe r) {
        return new Summary(r.id().value(), r.name().value(), r.status().name(), r.batchVolumeLiters(), r.version());
    }
}
