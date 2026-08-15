package br.com.brew.brassia.crm.application.port.inbound;

import br.com.brew.brassia.crm.domain.ContactPurpose;
import java.time.Instant;
import java.util.UUID;

/**
 * O que se faz com cliente, contato e consentimento (CRM-001).
 *
 * <p>Os comandos recebem o instante da decisão de fora, e não chamam {@code Instant.now()} por dentro:
 * uma decisão tomada por telefone na segunda e digitada na quarta precisa ser gravada com a data da
 * segunda, senão o livro de consentimento passa a contar a história da digitação em vez da do mundo.
 */
public interface CustomerCommands {

    UUID createCustomer(UUID breweryId, UUID actorId, String legalName, String tradeName, String taxId);

    void renameCustomer(UUID breweryId, UUID actorId, UUID customerId, String legalName, String tradeName);

    void setCustomerActive(UUID breweryId, UUID actorId, UUID customerId, boolean active);

    UUID createContact(UUID breweryId, UUID actorId, UUID customerId, String name, String email,
            String phone, String role);

    void recordConsent(UUID breweryId, UUID actorId, UUID contactId, ContactPurpose purpose, boolean granted,
            Instant decidedAt, String source);

    /** Apaga quem a pessoa era, mantendo a linha e o histórico de decisões. Irreversível. */
    void anonymizeContact(UUID breweryId, UUID actorId, UUID contactId);

    void setRetentionDays(UUID breweryId, UUID actorId, int days);
}
