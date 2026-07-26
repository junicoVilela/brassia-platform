package br.com.brew.brassia.planning.adapter.inbound.web.dto;

import br.com.brew.brassia.planning.domain.BrewOrder;
import java.math.BigDecimal;
import java.util.UUID;

/** OP detalhada, incluindo o snapshot congelado (cálculo da receita + equipamento). */
public record BrewOrderDetailView(UUID id, String code, UUID recipeId, int recipeVersion, BigDecimal volumeLiters,
        String status, RecipeSnapshotView recipe, EquipmentSnapshotView equipment) {

    public record RecipeSnapshotView(UUID id, int version, String name, BigDecimal ogSg, BigDecimal fgSg,
            BigDecimal abv, BigDecimal ibu, BigDecimal colorEbc) {}

    public record EquipmentSnapshotView(UUID id, BigDecimal capacityLiters, BigDecimal deadSpaceLiters,
            BigDecimal mashEfficiencyPercent, BigDecimal boilOffLitersPerHour) {}

    public static BrewOrderDetailView from(BrewOrder o) {
        var r = o.snapshot().recipe();
        var e = o.snapshot().equipment();
        return new BrewOrderDetailView(o.id().value(), o.code(), o.recipeId(), o.recipeVersion(),
                o.volumeLiters(), o.status().name(),
                new RecipeSnapshotView(r.id(), r.version(), r.name(), r.ogSg(), r.fgSg(), r.abv(), r.ibu(),
                        r.colorEbc()),
                new EquipmentSnapshotView(e.id(), e.capacityLiters(), e.deadSpaceLiters(),
                        e.mashEfficiencyPercent(), e.boilOffLitersPerHour()));
    }
}
