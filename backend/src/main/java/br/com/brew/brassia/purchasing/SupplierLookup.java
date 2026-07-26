package br.com.brew.brassia.purchasing;

import java.util.UUID;

/**
 * Consulta publicada de fornecedores, para outros módulos (ex.: estoque) validarem
 * a referência sem acessar a tabela de compras.
 */
public interface SupplierLookup {
    boolean exists(UUID breweryId, UUID supplierId);
}
