package br.com.brew.brassia.purchasing;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Fornecedor preferencial e custo de referência por ingrediente, derivados do
 * histórico de recebimento (lote mais recente). Porta declarada em compras
 * (inversão de dependência) e implementada pelo módulo de estoque, mantendo o
 * sentido inventory → purchasing sem ciclo. Usada na lista de compras (PUR-002).
 */
public interface IngredientSourcingLookup {
    List<Sourcing> preferredByIngredient(UUID breweryId);

    /**
     * @param supplierId          fornecedor do lote mais recente do ingrediente
     * @param unitCostPerCanonical custo do último lote convertido para a unidade canônica
     */
    record Sourcing(UUID ingredientId, UUID supplierId, BigDecimal unitCostPerCanonical) {}
}
