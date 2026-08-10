package br.com.brew.brassia.optimization.application.service;

import br.com.brew.brassia.catalog.IngredientPurchaseLookup;
import br.com.brew.brassia.catalog.IngredientSpecLookup;
import br.com.brew.brassia.optimization.domain.Candidate;
import br.com.brew.brassia.optimization.domain.ConstraintKind;
import br.com.brew.brassia.optimization.domain.Objective;
import br.com.brew.brassia.optimization.domain.OptimizationConstraint;
import br.com.brew.brassia.recipe.RecipeLookup;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A busca por substituições (OPT-001).
 *
 * <p><strong>Determinística por construção.</strong> Percorre as candidatas na ordem estável do catálogo
 * (por id) e não usa aleatoriedade em ponto nenhum. Rodar duas vezes sobre a mesma entrada devolve
 * exatamente o mesmo resultado, na mesma ordem — que é o que torna o número auditável seis meses depois.
 *
 * <p><strong>Restrições descartam; o objetivo ordena.</strong> As duas coisas não se misturam: uma
 * restrição violada elimina a candidata antes de qualquer score. Somá-la ao score com um peso alto
 * pareceria equivalente e não é — um peso, por maior que seja, é sempre comprável por um ganho maior, e
 * aí o resultado sai apresentado como ótimo tendo quebrado o que não podia.
 *
 * <p><strong>Uma substituição por vez</strong>, e isso é limitação declarada, não descuido: trocar dois
 * ingredientes simultaneamente multiplica o espaço de busca e, pior, produz alternativas cujo efeito
 * ninguém consegue atribuir a uma das trocas. Ver DEC-OPT-003.
 */
final class SubstitutionSolver {

    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);

    /** Quantas alternativas devolver. Mais que isso vira lista que ninguém lê; menos esconde opção. */
    static final int MAX_CANDIDATES = 5;

    private final IngredientSpecLookup specs;

    SubstitutionSolver(IngredientSpecLookup specs) {
        this.specs = specs;
    }

    record Context(
            UUID breweryId,
            RecipeLookup.PublishedComposition composition,
            Map<UUID, BigDecimal> costPerUnit,
            Map<UUID, BigDecimal> onHand,
            List<IngredientPurchaseLookup.PurchaseSpec> catalog,
            BigDecimal originalCostPerLiter,
            BigDecimal originalIbu,
            BigDecimal originalColorEbc) {
    }

    /**
     * Avalia as substituições possíveis.
     *
     * <p>Devolve lista vazia quando nada respeita as restrições — quem chama transforma isso em
     * inviabilidade com as restrições em conflito nomeadas.
     */
    List<Candidate> solve(Context context, Objective objective,
            List<OptimizationConstraint> constraints) {
        var candidates = new ArrayList<Candidate>();

        for (var item : context.composition().items()) {
            if (isKept(item.ingredientId(), constraints)) {
                continue;
            }
            var original = specOf(context, item.ingredientId());
            if (original == null) {
                // Ingrediente sem ficha técnica não se substitui: não há como estimar o efeito da troca,
                // e uma estimativa inventada é pior que a ausência da alternativa.
                continue;
            }
            for (var replacement : ordered(context.catalog())) {
                if (replacement.ingredientId().equals(item.ingredientId())) {
                    continue;
                }
                if (!admissible(context, replacement.ingredientId(), constraints)) {
                    continue;
                }
                var spec = specOf(context, replacement.ingredientId());
                if (spec == null || !sameType(original, spec)) {
                    // Trocar malte por lúpulo não é substituição, é outra receita.
                    continue;
                }
                var candidate = evaluate(context, item, replacement, spec, objective, constraints);
                if (candidate != null) {
                    candidates.add(candidate);
                }
            }
        }

        // Ordem estável: score primeiro, rótulo como desempate. Sem o desempate, duas candidatas de
        // mesmo score poderiam trocar de posição entre execuções — e a corrida deixaria de ser
        // reproduzível exatamente onde a diferença não importa, que é o pior lugar para descobrir isso.
        candidates.sort(Comparator.comparing(Candidate::score).reversed()
                .thenComparing(Candidate::label));
        return candidates.size() > MAX_CANDIDATES ? candidates.subList(0, MAX_CANDIDATES) : candidates;
    }

    private Candidate evaluate(Context context, RecipeLookup.CompositionItem item,
            IngredientPurchaseLookup.PurchaseSpec replacement, IngredientSpecLookup.Spec spec,
            Objective objective, List<OptimizationConstraint> constraints) {

        var newCost = costWithSubstitution(context, item, replacement.ingredientId());
        var newIbu = context.originalIbu();
        var newColor = context.originalColorEbc();

        var originalSpec = specOf(context, item.ingredientId());
        if (spec.colorEbc() != null && originalSpec != null && originalSpec.colorEbc() != null) {
            // Efeito de cor proporcional à quantidade do item trocado sobre o volume do lote.
            var delta = spec.colorEbc().subtract(originalSpec.colorEbc())
                    .multiply(item.quantity(), MC)
                    .divide(context.composition().batchVolumeLiters(), MC);
            newColor = newColor == null ? null : newColor.add(delta, MC);
        }
        if (spec.alphaAcidPercent() != null && originalSpec != null
                && originalSpec.alphaAcidPercent() != null && originalSpec.alphaAcidPercent().signum() > 0) {
            var ratio = spec.alphaAcidPercent().divide(originalSpec.alphaAcidPercent(), MC);
            newIbu = newIbu == null ? null : newIbu.multiply(ratio, MC);
        }

        // As restrições eliminam ANTES do score. É o ponto: violação não é desvantagem, é exclusão.
        for (var constraint : constraints) {
            var admits = switch (constraint.kind()) {
                case MAX_COST_PER_LITER -> constraint.admits(newCost);
                case IBU_RANGE -> constraint.admits(newIbu);
                case COLOR_RANGE -> constraint.admits(newColor);
                default -> true;
            };
            if (!admits) {
                return null;
            }
        }

        var score = scoreOf(context, objective, replacement.ingredientId(), newCost, newIbu, newColor);
        return new Candidate(
                "Trocar por " + replacement.name(),
                List.of(new Candidate.Substitution(item.ingredientId(), labelOf(context,
                        item.ingredientId()), replacement.ingredientId(), replacement.name(),
                        item.quantity(), item.unit())),
                scaled(newCost), scaled(newIbu), scaled(newColor), scaled(score),
                tradeOffsOf(context, newCost, newIbu, newColor));
    }

    /**
     * O score do objetivo escolhido, e só dele.
     *
     * <p>Normalizado como ganho relativo à receita original: um score que fosse o valor absoluto
     * dependeria da escala da grandeza, e comparar 3,20 R$/L com 32 IBU não significa nada.
     */
    private BigDecimal scoreOf(Context context, Objective objective, UUID replacementId,
            BigDecimal cost, BigDecimal ibu, BigDecimal color) {
        return switch (objective) {
            case COST -> relativeGain(context.originalCostPerLiter(), cost);
            case AVAILABILITY -> context.onHand().getOrDefault(replacementId, BigDecimal.ZERO);
            // Alvo técnico: quanto mais perto do original, melhor — a troca deve mudar o custo ou a
            // disponibilidade sem mexer no que a receita se propõe a ser.
            case TECHNICAL_TARGET -> BigDecimal.ZERO
                    .subtract(distance(context.originalIbu(), ibu))
                    .subtract(distance(context.originalColorEbc(), color));
        };
    }

    private List<Candidate.TradeOff> tradeOffsOf(Context context, BigDecimal cost, BigDecimal ibu,
            BigDecimal color) {
        // Só o que PIOROU: listar as melhorias aqui diluiria a leitura e faria o custo parecer menor.
        var tradeOffs = new ArrayList<Candidate.TradeOff>();
        if (greater(cost, context.originalCostPerLiter())) {
            tradeOffs.add(new Candidate.TradeOff("Custo por litro", "Fica mais cara",
                    scaled(context.originalCostPerLiter()), scaled(cost)));
        }
        if (differs(context.originalColorEbc(), color)) {
            tradeOffs.add(new Candidate.TradeOff("Cor (EBC)",
                    greater(color, context.originalColorEbc()) ? "A cerveja fica mais escura"
                            : "A cerveja fica mais clara",
                    scaled(context.originalColorEbc()), scaled(color)));
        }
        if (differs(context.originalIbu(), ibu)) {
            tradeOffs.add(new Candidate.TradeOff("IBU",
                    greater(ibu, context.originalIbu()) ? "Fica mais amarga" : "Fica menos amarga",
                    scaled(context.originalIbu()), scaled(ibu)));
        }
        return tradeOffs;
    }

    private BigDecimal costWithSubstitution(Context context, RecipeLookup.CompositionItem item,
            UUID replacementId) {
        var total = BigDecimal.ZERO;
        for (var each : context.composition().items()) {
            var ingredientId = each.ingredientId().equals(item.ingredientId())
                    ? replacementId : each.ingredientId();
            var unitCost = context.costPerUnit().getOrDefault(ingredientId, BigDecimal.ZERO);
            total = total.add(unitCost.multiply(each.quantity(), MC), MC);
        }
        return total.divide(context.composition().batchVolumeLiters(), MC);
    }

    private static boolean isKept(UUID ingredientId, List<OptimizationConstraint> constraints) {
        return constraints.stream().anyMatch(c -> c.kind() == ConstraintKind.KEEP_INGREDIENT
                && c.ingredient().filter(ingredientId::equals).isPresent());
    }

    private static boolean admissible(Context context, UUID ingredientId,
            List<OptimizationConstraint> constraints) {
        for (var constraint : constraints) {
            if (constraint.kind() == ConstraintKind.EXCLUDE_INGREDIENT
                    && constraint.ingredient().filter(ingredientId::equals).isPresent()) {
                return false;
            }
            if (constraint.kind() == ConstraintKind.STOCK_ONLY
                    && context.onHand().getOrDefault(ingredientId, BigDecimal.ZERO).signum() <= 0) {
                return false;
            }
        }
        return true;
    }

    /** Ordem estável do catálogo: o id. Sem ela, a enumeração deixaria de ser reproduzível. */
    private static List<IngredientPurchaseLookup.PurchaseSpec> ordered(
            List<IngredientPurchaseLookup.PurchaseSpec> catalog) {
        return catalog.stream()
                .sorted(Comparator.comparing(s -> s.ingredientId().toString()))
                .toList();
    }

    private IngredientSpecLookup.Spec specOf(Context context, UUID ingredientId) {
        return specs.find(context.breweryId(), ingredientId).orElse(null);
    }

    private static boolean sameType(IngredientSpecLookup.Spec a, IngredientSpecLookup.Spec b) {
        return a.type() != null && a.type().equals(b.type());
    }

    private static String labelOf(Context context, UUID ingredientId) {
        return context.catalog().stream().filter(s -> s.ingredientId().equals(ingredientId))
                .map(IngredientPurchaseLookup.PurchaseSpec::name).findFirst()
                .orElse(ingredientId.toString());
    }

    private static BigDecimal relativeGain(BigDecimal original, BigDecimal candidate) {
        if (original == null || candidate == null || original.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return original.subtract(candidate).divide(original, MC);
    }

    private static BigDecimal distance(BigDecimal original, BigDecimal candidate) {
        return original == null || candidate == null ? BigDecimal.ZERO
                : original.subtract(candidate).abs();
    }

    private static boolean greater(BigDecimal a, BigDecimal b) {
        return a != null && b != null && a.compareTo(b) > 0;
    }

    private static boolean differs(BigDecimal a, BigDecimal b) {
        return a != null && b != null && a.compareTo(b) != 0;
    }

    private static BigDecimal scaled(BigDecimal value) {
        return value == null ? null : value.setScale(4, RoundingMode.HALF_UP);
    }
}
