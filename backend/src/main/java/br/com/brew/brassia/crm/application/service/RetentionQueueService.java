package br.com.brew.brassia.crm.application.service;

import br.com.brew.brassia.crm.application.port.outbound.ContactRepository;
import br.com.brew.brassia.crm.application.port.outbound.RetentionPolicyRepository;
import br.com.brew.brassia.crm.domain.LastRelationship;
import br.com.brew.brassia.distribution.CustomerDeliveryLookup;
import br.com.brew.brassia.sales.CustomerActivityLookup;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quem já passou do prazo de retenção — e por quê (DUV-CRM-001).
 *
 * <p><strong>Isto lista; não apaga.</strong> A decisão do mantenedor foi manter a anonimização como ato
 * humano: apagar dado pessoal é irreversível, e uma varredura automática com um bug de data apagaria
 * contatos de clientes ativos. Mas exigir revisão manual sem dizer <em>quem</em> venceu faz a fila crescer
 * até ninguém olhar — e era essa metade que faltava.
 *
 * <p><strong>Cada linha diz de onde veio a data.</strong> "Vence em março" sem dizer que a conta partiu de
 * uma entrega de 2024 é um número que ninguém consegue conferir, e conferir é o ponto.
 */
public class RetentionQueueService {

    private final ContactRepository contacts;
    private final RetentionPolicyRepository policies;
    private final CustomerActivityLookup orders;
    private final CustomerDeliveryLookup deliveries;

    public RetentionQueueService(ContactRepository contacts, RetentionPolicyRepository policies,
            CustomerActivityLookup orders, CustomerDeliveryLookup deliveries) {
        this.contacts = Objects.requireNonNull(contacts);
        this.policies = Objects.requireNonNull(policies);
        this.orders = Objects.requireNonNull(orders);
        this.deliveries = Objects.requireNonNull(deliveries);
    }

    /**
     * A fila do dia.
     *
     * <p>Vazia quando não há política de retenção: sem prazo definido nada vence, e mostrar uma fila
     * baseada num prazo que ninguém escolheu convidaria a anonimizar por engano.
     */
    @Transactional(readOnly = true)
    public List<DueContact> due(UUID breweryId, LocalDate today) {
        var policy = policies.find(breweryId);
        if (policy.daysAfterLastInteraction().isEmpty()) {
            return List.of();
        }
        return contacts.liveContacts(breweryId).stream()
                .map(c -> avaliar(breweryId, c, policy, today))
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<DueContact> avaliar(UUID breweryId,
            ContactRepository.ContactRelationship contact,
            br.com.brew.brassia.crm.domain.RetentionPolicy policy, LocalDate today) {
        var relationship = LastRelationship.of(
                orders.lastOrderOn(breweryId, contact.customerId()).orElse(null),
                deliveries.lastDeliveryOn(breweryId, contact.customerId()).orElse(null),
                contact.lastConsentOn());

        var ultimo = relationship.latest().orElse(null);
        if (ultimo == null) {
            // Cadastro que nunca foi usado não é cliente vencido. Tratar a ausência de evidência como
            // evidência de ausência anonimizaria justamente quem acabou de ser cadastrado.
            return Optional.empty();
        }
        if (!policy.dueFor(ultimo, today)) {
            return Optional.empty();
        }
        return Optional.of(new DueContact(contact.contactId(), contact.customerId(), contact.name(),
                ultimo, relationship.source().orElse(null),
                policy.anonymizeOn(ultimo).orElse(null)));
    }

    /**
     * @param source de onde veio a data — é o que permite conferir antes de um ato irreversível
     */
    public record DueContact(UUID contactId, UUID customerId, String name, LocalDate lastRelationship,
            String source, LocalDate dueSince) {}
}
