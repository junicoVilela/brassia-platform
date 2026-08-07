package br.com.brew.brassia.planning;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * O plano de material de uma ordem (CST-002): quanto a receita pedia para aquele volume.
 *
 * <p>É a mesma explosão que alimenta a necessidade de compra (PLN-002), recortada para uma ordem
 * só. Quem escala receita por volume é o planejamento, e refazer essa conta dentro do custo criaria
 * duas fórmulas que um dia divergiriam.
 *
 * <p><strong>A versão viaja junto porque o plano pode ter mudado depois.</strong> A ordem congelou
 * a versão da receita com que foi criada; a explosão sai da versão publicada hoje. Quando as duas
 * diferem, comparar consumo com esse plano é comparar com uma receita que ninguém brassou — e o
 * custo prefere declarar que não tem base a apresentar uma variação inventada.
 */
public interface OrderPlanLookup {

    Optional<OrderPlan> planOf(UUID breweryId, UUID orderId);

    /**
     * @param plannedVolumeLiters   volume que a ordem pediu
     * @param orderRecipeVersion    versão da receita congelada na ordem
     * @param plannedRecipeVersion  versão publicada de onde saiu esta explosão; vazia quando a
     *                              receita não está mais publicada
     */
    record OrderPlan(BigDecimal plannedVolumeLiters, int orderRecipeVersion,
            Integer plannedRecipeVersion, List<PlannedMaterial> materials) {

        public OrderPlan {
            materials = List.copyOf(materials);
        }

        /** Verdadeiro quando o plano é o da receita que a ordem congelou. */
        public boolean baselineMatchesOrder() {
            return plannedRecipeVersion != null && plannedRecipeVersion == orderRecipeVersion;
        }
    }

    /** Quantidade planejada de um ingrediente, na unidade canônica (KG, L, UNIT). */
    record PlannedMaterial(UUID ingredientId, BigDecimal quantity, String unit) {}
}
