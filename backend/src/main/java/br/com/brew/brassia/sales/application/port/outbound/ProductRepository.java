package br.com.brew.brassia.sales.application.port.outbound;

import br.com.brew.brassia.sales.domain.Product;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistência dos produtos (SAL-001).
 *
 * <p>Toda assinatura carrega {@code breweryId} porque o {@code TenantIsolationTest} exige filtro por
 * cervejaria em toda escrita — a garantia não pode depender de o handler ter lembrado de carregar a
 * entidade com escopo antes.
 */
public interface ProductRepository {

    void insert(Product product, UUID actorId);

    void update(Product product);

    Optional<Product> find(UUID breweryId, UUID id);

    List<Product> list(UUID breweryId, boolean onlyActive);

    boolean skuTaken(UUID breweryId, String sku);
}
