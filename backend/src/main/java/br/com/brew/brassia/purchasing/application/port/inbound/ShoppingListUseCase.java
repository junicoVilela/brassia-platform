package br.com.brew.brassia.purchasing.application.port.inbound;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Lista de compras consolidada por fornecedor (PUR-002): agrupa as necessidades
 * (PUR-001) pelo fornecedor preferencial de cada ingrediente, converte para a
 * unidade de compra e, opcionalmente, estima custo. Leitura — não cria pedido.
 */
public interface ShoppingListUseCase {

    /** @param includeCosts expõe custo unitário/total (gated por permissão no controlador) */
    List<SupplierGroup> handle(UUID breweryId, boolean includeCosts);

    /**
     * @param supplierId    fornecedor preferencial; nulo quando não há histórico
     * @param supplierName  nome do fornecedor ou rótulo do grupo sem fornecedor
     * @param items         itens a comprar deste fornecedor
     * @param estimatedTotal soma dos custos estimados (nulo quando custos omitidos)
     */
    record SupplierGroup(UUID supplierId, String supplierName, List<Item> items, BigDecimal estimatedTotal) {}

    /**
     * @param demand         necessidade bruta (unidade técnica canônica)
     * @param onHand         saldo em mãos (canônico)
     * @param reserved       reservado para outras ordens (canônico)
     * @param suggested      a comprar na unidade técnica canônica
     * @param unit           unidade técnica canônica (KG/L/UNIT)
     * @param purchaseQuantity a comprar convertido para a unidade de compra
     * @param purchaseUnit   unidade de compra do catálogo (fallback = unidade técnica)
     * @param unitCost       custo por unidade técnica (nulo se omitido/sem histórico)
     * @param estimatedCost  suggested × unitCost (nulo se omitido/sem histórico)
     */
    record Item(UUID ingredientId, String ingredientCode, String ingredientName,
            BigDecimal demand, BigDecimal onHand, BigDecimal reserved, BigDecimal suggested, String unit,
            BigDecimal purchaseQuantity, String purchaseUnit,
            BigDecimal unitCost, BigDecimal estimatedCost) {}
}
