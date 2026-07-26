package br.com.brew.brassia.planning;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Consulta publicada da demanda de materiais das ordens de produção liberadas,
 * agregada por ingrediente (unidade canônica), para o cálculo de necessidade de
 * compra (PUR-001) sem acessar tabelas do planejamento.
 */
public interface OrderDemandLookup {
    List<IngredientDemand> aggregatedDemand(UUID breweryId);

    record IngredientDemand(UUID ingredientId, BigDecimal quantity, String unit) {}
}
