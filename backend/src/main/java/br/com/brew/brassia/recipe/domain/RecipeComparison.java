package br.com.brew.brassia.recipe.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Compara duas receitas campo a campo (REC-005), listando apenas as diferenças —
 * escalares (nome, volume, fervura, equipamento, metas) e por item da composição
 * (adicionado, removido ou quantidade/unidade alterada), com chave etapa+ingrediente.
 */
public final class RecipeComparison {
    private RecipeComparison() {
    }

    public static Result compare(Recipe left, Recipe right) {
        var diffs = new ArrayList<Difference>();
        scalar(diffs, "name", left.name().value(), right.name().value());
        scalar(diffs, "batchVolumeLiters", left.batchVolumeLiters(), right.batchVolumeLiters());
        scalar(diffs, "boilTimeMinutes", left.boilTimeMinutes(), right.boilTimeMinutes());
        scalar(diffs, "equipmentId", left.equipmentId(), right.equipmentId());
        scalar(diffs, "targetOgPoints", left.targets().ogPoints(), right.targets().ogPoints());
        scalar(diffs, "targetIbu", left.targets().ibu(), right.targets().ibu());
        scalar(diffs, "targetColorEbc", left.targets().colorEbc(), right.targets().colorEbc());
        scalar(diffs, "targetAbv", left.targets().abv(), right.targets().abv());
        compareItems(diffs, left, right);
        return new Result(diffs);
    }

    private static void scalar(List<Difference> diffs, String field, Object a, Object b) {
        if (!valuesEqual(a, b)) {
            diffs.add(new Difference(field, text(a), text(b)));
        }
    }

    private static void compareItems(List<Difference> diffs, Recipe left, Recipe right) {
        var leftItems = keyed(left);
        var rightItems = keyed(right);
        var keys = new LinkedHashSet<String>();
        keys.addAll(leftItems.keySet());
        keys.addAll(rightItems.keySet());
        for (var key : keys) {
            var a = leftItems.get(key);
            var b = rightItems.get(key);
            if (a == null) {
                diffs.add(new Difference("item[" + key + "]", null, describe(b)));
            } else if (b == null) {
                diffs.add(new Difference("item[" + key + "]", describe(a), null));
            } else if (!describe(a).equals(describe(b))) {
                diffs.add(new Difference("item[" + key + "]", describe(a), describe(b)));
            }
        }
    }

    private static LinkedHashMap<String, RecipeItem> keyed(Recipe recipe) {
        var map = new LinkedHashMap<String, RecipeItem>();
        for (var item : recipe.items()) {
            map.put(item.stage().name() + ":" + item.ingredientId(), item);
        }
        return map;
    }

    private static String describe(RecipeItem item) {
        return item.quantity().stripTrailingZeros().toPlainString() + " " + item.unit().name();
    }

    private static boolean valuesEqual(Object a, Object b) {
        if (a instanceof BigDecimal da && b instanceof BigDecimal db) {
            return da.compareTo(db) == 0;
        }
        return Objects.equals(a, b);
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal d) {
            return d.stripTrailingZeros().toPlainString();
        }
        return value.toString();
    }

    public record Difference(String field, String left, String right) {}

    public record Result(List<Difference> differences) {}
}
