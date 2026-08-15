package br.com.brew.brassia.crm.application.service;

import br.com.brew.brassia.crm.application.port.inbound.CustomerCommands;
import br.com.brew.brassia.crm.application.port.outbound.ContactRepository;
import br.com.brew.brassia.crm.application.port.outbound.CustomerRepository;
import br.com.brew.brassia.crm.application.port.outbound.RetentionPolicyRepository;
import br.com.brew.brassia.crm.domain.Contact;
import br.com.brew.brassia.crm.domain.ConsentDecision;
import br.com.brew.brassia.crm.domain.ConsentEntry;
import br.com.brew.brassia.crm.domain.ContactPurpose;
import br.com.brew.brassia.crm.domain.Customer;
import br.com.brew.brassia.crm.domain.DuplicateTaxIdException;
import br.com.brew.brassia.crm.domain.UnknownCustomerException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/** Casos de uso da CRM-001. */
public class CustomerHandlers implements CustomerCommands {

    private final CustomerRepository customers;
    private final ContactRepository contacts;
    private final RetentionPolicyRepository retention;

    public CustomerHandlers(CustomerRepository customers, ContactRepository contacts,
            RetentionPolicyRepository retention) {
        this.customers = Objects.requireNonNull(customers);
        this.contacts = Objects.requireNonNull(contacts);
        this.retention = Objects.requireNonNull(retention);
    }

    @Override
    @Transactional
    public UUID createCustomer(UUID breweryId, UUID actorId, String legalName, String tradeName,
            String taxId) {
        var customer = Customer.create(UUID.randomUUID(), breweryId, legalName, tradeName, taxId,
                Instant.now());
        requireTaxIdFree(breweryId, customer.taxId().orElse(null), null);
        customers.insert(customer, actorId);
        return customer.id();
    }

    @Override
    @Transactional
    public void renameCustomer(UUID breweryId, UUID actorId, UUID customerId, String legalName,
            String tradeName) {
        var customer = requireCustomer(breweryId, customerId);
        customer.rename(legalName, tradeName);
        customers.update(customer);
    }

    @Override
    @Transactional
    public void setCustomerActive(UUID breweryId, UUID actorId, UUID customerId, boolean active) {
        var customer = requireCustomer(breweryId, customerId);
        if (active) {
            customer.reactivate();
        } else {
            customer.deactivate();
        }
        customers.update(customer);
    }

    @Override
    @Transactional
    public UUID createContact(UUID breweryId, UUID actorId, UUID customerId, String name, String email,
            String phone, String role) {
        // A existência do cliente é conferida aqui e garantida pela chave estrangeira. A checagem serve
        // para a mensagem; a garantia é do banco, porque checagem prévia não sobrevive a duas
        // requisições simultâneas.
        requireCustomer(breweryId, customerId);
        var contact = Contact.create(UUID.randomUUID(), breweryId, customerId, name, email, phone, role);
        contacts.insert(contact, actorId);
        return contact.id();
    }

    @Override
    @Transactional
    public void recordConsent(UUID breweryId, UUID actorId, UUID contactId, ContactPurpose purpose,
            boolean granted, Instant decidedAt, String source) {
        var contact = requireContact(breweryId, contactId);
        // O agregado é quem recusa contato anonimizado e finalidade contratual — o handler não repete
        // essas regras, senão elas passariam a existir em dois lugares e divergiriam na primeira mudança.
        if (granted) {
            contact.grant(purpose, decidedAt, source, actorId);
        } else {
            contact.revoke(purpose, decidedAt, source, actorId);
        }
        contacts.appendConsent(breweryId, contactId,
                new ConsentEntry(purpose, granted ? ConsentDecision.GRANTED : ConsentDecision.REVOKED,
                        decidedAt, source, actorId));
    }

    @Override
    @Transactional
    public void anonymizeContact(UUID breweryId, UUID actorId, UUID contactId) {
        var contact = requireContact(breweryId, contactId);
        contact.anonymize(Instant.now());
        contacts.anonymize(contact);
    }

    @Override
    @Transactional
    public void setRetentionDays(UUID breweryId, UUID actorId, int days) {
        retention.save(breweryId, days, actorId);
    }

    private Customer requireCustomer(UUID breweryId, UUID customerId) {
        return customers.find(breweryId, customerId)
                .orElseThrow(() -> new UnknownCustomerException("o cliente", customerId));
    }

    private Contact requireContact(UUID breweryId, UUID contactId) {
        return contacts.find(breweryId, contactId)
                .orElseThrow(() -> new UnknownCustomerException("o contato", contactId));
    }

    private void requireTaxIdFree(UUID breweryId, String taxId, UUID exceptId) {
        if (taxId != null && customers.taxIdTaken(breweryId, taxId, exceptId)) {
            throw new DuplicateTaxIdException(taxId, "outro cliente desta cervejaria");
        }
    }
}
