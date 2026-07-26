package br.com.brew.brassia.purchasing;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Porta que o cálculo de necessidade de compra precisa: saldo em mãos por
 * ingrediente, em unidade canônica. Declarada aqui (inversão de dependência)
 * para que o módulo de estoque a implemente sem que compras dependa de estoque —
 * o sentido inventory → purchasing já existe (SupplierLookup), evitando ciclo.
 */
public interface StockOnHandLookup {
    List<IngredientOnHand> onHandByIngredient(UUID breweryId);

    record IngredientOnHand(UUID ingredientId, BigDecimal onHand, BigDecimal reserved, String unit) {}
}
