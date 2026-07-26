package br.com.brew.brassia.catalog;

import java.util.List;
import java.util.UUID;

/**
 * Dados de compra dos ingredientes do catálogo (unidade de compra + identificação),
 * para a lista de compras consolidar quantidades na unidade de aquisição (PUR-002)
 * sem acessar a tabela do catálogo diretamente.
 */
public interface IngredientPurchaseLookup {
    List<PurchaseSpec> findAll(UUID breweryId);

    record PurchaseSpec(UUID ingredientId, String code, String name, String useUnit, String purchaseUnit) {}
}
