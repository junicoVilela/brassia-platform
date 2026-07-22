package br.com.brew.brassia.catalog.adapter.inbound.web.dto;

import br.com.brew.brassia.catalog.application.port.inbound.ListIngredientsUseCase;
import br.com.brew.brassia.catalog.application.port.inbound.RegisterIngredientUseCase;
import br.com.brew.brassia.catalog.application.port.inbound.UpdateIngredientUseCase;
import java.util.Map;
import java.util.UUID;

public record IngredientResponse(
        UUID id,
        String type,
        String code,
        String name,
        String useUnit,
        String purchaseUnit,
        Map<String, String> attributes,
        boolean active,
        long version) {

    public static IngredientResponse from(RegisterIngredientUseCase.Result r) {
        return new IngredientResponse(r.id(), r.type(), r.code(), r.name(), r.useUnit(), r.purchaseUnit(),
                r.attributes(), r.active(), r.version());
    }

    public static IngredientResponse from(UpdateIngredientUseCase.Result r) {
        return new IngredientResponse(r.id(), r.type(), r.code(), r.name(), r.useUnit(), r.purchaseUnit(),
                r.attributes(), r.active(), r.version());
    }

    public static IngredientResponse from(ListIngredientsUseCase.Summary s) {
        return new IngredientResponse(s.id(), s.type(), s.code(), s.name(), s.useUnit(), s.purchaseUnit(),
                s.attributes(), s.active(), s.version());
    }
}
