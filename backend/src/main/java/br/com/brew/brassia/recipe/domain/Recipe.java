package br.com.brew.brassia.recipe.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Receita cervejeira (REC-001): composição, equipamento, metas e processo.
 * Nasce em rascunho. Invariantes: volume da batelada positivo e não superior à
 * capacidade do equipamento; ao menos um item; percentuais de mostura (quando
 * informados) somam 100.
 */
public final class Recipe {
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal PCT_TOLERANCE = new BigDecimal("0.1");

    private final RecipeId id;
    private final UUID breweryId;
    private RecipeName name;
    private RecipeStatus status;
    private final UUID equipmentId;
    private BigDecimal batchVolumeLiters;
    private RecipeTargets targets;
    private Integer boilTimeMinutes;
    private final List<RecipeItem> items;
    private final long version;

    private Recipe(RecipeId id, UUID breweryId, RecipeName name, RecipeStatus status, UUID equipmentId,
            BigDecimal batchVolumeLiters, RecipeTargets targets, Integer boilTimeMinutes,
            List<RecipeItem> items, long version) {
        this.id = Objects.requireNonNull(id);
        this.breweryId = Objects.requireNonNull(breweryId);
        this.name = Objects.requireNonNull(name);
        this.status = Objects.requireNonNull(status);
        this.equipmentId = Objects.requireNonNull(equipmentId, "equipmentId");
        this.batchVolumeLiters = requirePositive(batchVolumeLiters);
        this.targets = Objects.requireNonNull(targets);
        this.boilTimeMinutes = requireNonNegative(boilTimeMinutes);
        this.items = List.copyOf(Objects.requireNonNull(items, "items"));
        this.version = version;
    }

    /**
     * @param capacityLiters capacidade do equipamento referenciado (consulta publicada)
     */
    public static Recipe draft(UUID breweryId, String name, UUID equipmentId, BigDecimal batchVolumeLiters,
            BigDecimal capacityLiters, RecipeTargets targets, Integer boilTimeMinutes, List<RecipeItem> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("receita precisa de ao menos um item");
        }
        if (capacityLiters != null && batchVolumeLiters != null
                && batchVolumeLiters.compareTo(capacityLiters) > 0) {
            throw new IllegalArgumentException("volume da batelada excede a capacidade do equipamento");
        }
        validateMashPercentages(items);
        return new Recipe(RecipeId.newId(), breweryId, new RecipeName(name), RecipeStatus.DRAFT, equipmentId,
                batchVolumeLiters, targets == null ? RecipeTargets.none() : targets, boilTimeMinutes, items, 0);
    }

    public static Recipe reconstitute(RecipeId id, UUID breweryId, RecipeName name, RecipeStatus status,
            UUID equipmentId, BigDecimal batchVolumeLiters, RecipeTargets targets, Integer boilTimeMinutes,
            List<RecipeItem> items, long version) {
        return new Recipe(id, breweryId, name, status, equipmentId, batchVolumeLiters, targets, boilTimeMinutes,
                items, version);
    }

    private static void validateMashPercentages(List<RecipeItem> items) {
        var mash = items.stream().filter(i -> i.stage() == RecipeStage.MASH).toList();
        var withPct = mash.stream().filter(i -> i.percentage() != null).toList();
        if (withPct.isEmpty()) {
            return;
        }
        if (withPct.size() != mash.size()) {
            throw new IllegalArgumentException("todos os itens de mostura precisam de percentual quando um tem");
        }
        var sum = withPct.stream().map(RecipeItem::percentage).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.subtract(HUNDRED).abs().compareTo(PCT_TOLERANCE) > 0) {
            throw new IllegalArgumentException("percentuais de mostura devem somar 100");
        }
    }

    private static BigDecimal requirePositive(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException("volume da batelada deve ser positivo");
        }
        return value;
    }

    private static Integer requireNonNegative(Integer value) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException("tempo de fervura não pode ser negativo");
        }
        return value;
    }

    public RecipeId id() { return id; }
    public UUID breweryId() { return breweryId; }
    public RecipeName name() { return name; }
    public RecipeStatus status() { return status; }
    public UUID equipmentId() { return equipmentId; }
    public BigDecimal batchVolumeLiters() { return batchVolumeLiters; }
    public RecipeTargets targets() { return targets; }
    public Integer boilTimeMinutes() { return boilTimeMinutes; }
    public List<RecipeItem> items() { return items; }
    public long version() { return version; }
}
