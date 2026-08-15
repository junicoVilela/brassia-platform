package br.com.brew.brassia.crm.application.port.outbound;

import br.com.brew.brassia.crm.domain.Customer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistência dos clientes (CRM-001).
 *
 * <p>Toda assinatura carrega {@code breweryId} — não como parâmetro de conveniência, e sim porque o
 * {@code TenantIsolationTest} exige que a escrita filtre por cervejaria. Buscar só pelo id deixaria a
 * garantia dependendo de o handler ter lembrado de carregar a entidade com escopo antes.
 */
public interface CustomerRepository {

    void insert(Customer customer, UUID actorId);

    void update(Customer customer);

    Optional<Customer> find(UUID breweryId, UUID id);

    List<Customer> list(UUID breweryId, boolean onlyActive);

    /** Se já existe outro cliente com o mesmo documento na cervejaria. */
    boolean taxIdTaken(UUID breweryId, String taxId, UUID exceptId);
}
