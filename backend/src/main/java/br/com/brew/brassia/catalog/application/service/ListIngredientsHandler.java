package br.com.brew.brassia.catalog.application.service;

import br.com.brew.brassia.catalog.application.port.inbound.ListIngredientsUseCase;
import br.com.brew.brassia.catalog.application.port.outbound.IngredientRepository;
import br.com.brew.brassia.catalog.domain.Ingredient;
import br.com.brew.brassia.catalog.domain.IngredientType;
import java.util.Objects;

public final class ListIngredientsHandler implements ListIngredientsUseCase {
    private final IngredientRepository repository;

    public ListIngredientsHandler(IngredientRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Override
    public Result handle(Query query) {
        var type = query.type() == null || query.type().isBlank() ? null : IngredientType.of(query.type());
        var content = repository.findPage(query.breweryId(), type, query.page(), query.size())
                .stream().map(ListIngredientsHandler::toSummary).toList();
        var total = repository.count(query.breweryId(), type);
        var totalPages = query.size() == 0 ? 0 : (int) Math.ceil((double) total / query.size());
        return new Result(content, query.page(), query.size(), total, totalPages);
    }

    private static Summary toSummary(Ingredient i) {
        return new Summary(i.id().value(), i.type().name(), i.code().value(), i.name().value(),
                i.useUnit().name(), i.purchaseUnit().name(), i.attributes(), i.active(), i.version());
    }
}
