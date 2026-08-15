package br.com.brew.brassia.crm.adapter.inbound.web;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.crm.adapter.inbound.web.dto.CrmDtos.ConsentEntryView;
import br.com.brew.brassia.crm.adapter.inbound.web.dto.CrmDtos.ContactView;
import br.com.brew.brassia.crm.adapter.inbound.web.dto.CrmDtos.CreateContactRequest;
import br.com.brew.brassia.crm.adapter.inbound.web.dto.CrmDtos.CreateCustomerRequest;
import br.com.brew.brassia.crm.adapter.inbound.web.dto.CrmDtos.CustomerView;
import br.com.brew.brassia.crm.adapter.inbound.web.dto.CrmDtos.PurposeView;
import br.com.brew.brassia.crm.adapter.inbound.web.dto.CrmDtos.RecordConsentRequest;
import br.com.brew.brassia.crm.adapter.inbound.web.dto.CrmDtos.RenameCustomerRequest;
import br.com.brew.brassia.crm.adapter.inbound.web.dto.CrmDtos.SetActiveRequest;
import br.com.brew.brassia.crm.application.port.inbound.CustomerCommands;
import br.com.brew.brassia.crm.application.port.outbound.ContactRepository;
import br.com.brew.brassia.crm.application.port.outbound.CustomerRepository;
import br.com.brew.brassia.crm.domain.Contact;
import br.com.brew.brassia.crm.domain.ContactPurpose;
import br.com.brew.brassia.crm.domain.Customer;
import br.com.brew.brassia.crm.domain.UnknownCustomerException;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Clientes, contatos e consentimentos (CRM-001).
 *
 * <p><strong>Não há DELETE.</strong> Cliente desativa e contato anonimiza — as duas operações preservam
 * a linha, porque é o histórico de expedição que um recall percorre para saber a quem avisar. Um DELETE
 * aqui deixaria expedição apontando para o nada exatamente quando ela mais importa.
 */
@RestController
@RequestMapping("/api/v1/crm/customers")
final class CustomerController {

    private final CustomerCommands commands;
    private final CustomerRepository customers;
    private final ContactRepository contacts;
    private final AuditTrail audit;

    CustomerController(CustomerCommands commands, CustomerRepository customers, ContactRepository contacts,
            AuditTrail audit) {
        this.commands = Objects.requireNonNull(commands);
        this.customers = Objects.requireNonNull(customers);
        this.contacts = Objects.requireNonNull(contacts);
        this.audit = Objects.requireNonNull(audit);
    }

    @GetMapping
    List<CustomerView> list(@AuthenticationPrincipal SecurityPrincipal principal,
            @RequestParam(defaultValue = "true") boolean onlyActive) {
        principal.requirePermission("crm.customer.read");
        return customers.list(principal.requireBrewery(), onlyActive).stream()
                .map(CustomerController::view).toList();
    }

    @GetMapping("/{id}")
    CustomerView get(@AuthenticationPrincipal SecurityPrincipal principal, @PathVariable UUID id) {
        principal.requirePermission("crm.customer.read");
        return customers.find(principal.requireBrewery(), id).map(CustomerController::view)
                .orElseThrow(() -> new UnknownCustomerException("o cliente", id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, UUID> create(@AuthenticationPrincipal SecurityPrincipal principal,
            @Valid @RequestBody CreateCustomerRequest request) {
        principal.requirePermission("crm.customer.manage");
        var brewery = principal.requireBrewery();
        var id = commands.createCustomer(brewery, principal.userId(), request.legalName(),
                request.tradeName(), request.taxId());
        audit.record(AuditEvent.success(brewery, principal.userId(), "crm.customer.create",
                "crm.customer", id.toString(), Map.of("legalName", request.legalName())));
        return Map.of("id", id);
    }

    @PutMapping("/{id}")
    void rename(@AuthenticationPrincipal SecurityPrincipal principal, @PathVariable UUID id,
            @Valid @RequestBody RenameCustomerRequest request) {
        principal.requirePermission("crm.customer.manage");
        var brewery = principal.requireBrewery();
        commands.renameCustomer(brewery, principal.userId(), id, request.legalName(), request.tradeName());
        audit.record(AuditEvent.success(brewery, principal.userId(), "crm.customer.rename",
                "crm.customer", id.toString(), Map.of("legalName", request.legalName())));
    }

    @PutMapping("/{id}/active")
    void setActive(@AuthenticationPrincipal SecurityPrincipal principal, @PathVariable UUID id,
            @Valid @RequestBody SetActiveRequest request) {
        principal.requirePermission("crm.customer.manage");
        var brewery = principal.requireBrewery();
        commands.setCustomerActive(brewery, principal.userId(), id, request.active());
        audit.record(AuditEvent.success(brewery, principal.userId(), "crm.customer.set-active",
                "crm.customer", id.toString(), Map.of("active", String.valueOf(request.active()))));
    }

    @GetMapping("/{id}/contacts")
    List<ContactView> contacts(@AuthenticationPrincipal SecurityPrincipal principal, @PathVariable UUID id) {
        principal.requirePermission("crm.customer.read");
        var brewery = principal.requireBrewery();
        customers.find(brewery, id).orElseThrow(() -> new UnknownCustomerException("o cliente", id));
        return contacts.listByCustomer(brewery, id).stream().map(CustomerController::view).toList();
    }

    @PostMapping("/{id}/contacts")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, UUID> createContact(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID id, @Valid @RequestBody CreateContactRequest request) {
        principal.requirePermission("crm.customer.manage");
        var brewery = principal.requireBrewery();
        var contactId = commands.createContact(brewery, principal.userId(), id, request.name(),
                request.email(), request.phone(), request.role());
        // O nome não vai para a auditoria: registrar quem é a pessoa num log que sobrevive à
        // anonimização recriaria, no rastro, o dado que ela pediu para apagar.
        audit.record(AuditEvent.success(brewery, principal.userId(), "crm.contact.create",
                "crm.contact", contactId.toString(), Map.of("customerId", id.toString())));
        return Map.of("id", contactId);
    }

    @PostMapping("/contacts/{contactId}/consents")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void recordConsent(@AuthenticationPrincipal SecurityPrincipal principal, @PathVariable UUID contactId,
            @Valid @RequestBody RecordConsentRequest request) {
        principal.requirePermission("crm.customer.manage");
        var brewery = principal.requireBrewery();
        commands.recordConsent(brewery, principal.userId(), contactId, request.purpose(),
                request.granted(), request.decidedAt(), request.source());
        audit.record(AuditEvent.success(brewery, principal.userId(), "crm.consent.record",
                "crm.contact", contactId.toString(),
                Map.of("purpose", request.purpose().name(), "granted", String.valueOf(request.granted()),
                        "decidedAt", request.decidedAt().toString(), "source", request.source())));
    }

    @PostMapping("/contacts/{contactId}/anonymize")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void anonymize(@AuthenticationPrincipal SecurityPrincipal principal, @PathVariable UUID contactId) {
        // Permissão própria e crítica: apagar uma pessoa é irreversível, e não é o mesmo ato de
        // cadastrar um contato.
        principal.requirePermission("crm.contact.anonymize");
        var brewery = principal.requireBrewery();
        commands.anonymizeContact(brewery, principal.userId(), contactId);
        audit.record(AuditEvent.success(brewery, principal.userId(), "crm.contact.anonymize",
                "crm.contact", contactId.toString(), Map.of()));
    }

    private static CustomerView view(Customer c) {
        return new CustomerView(c.id(), c.legalName(), c.tradeName().orElse(null), c.displayName(),
                c.taxId().orElse(null), c.isActive());
    }

    private static ContactView view(Contact c) {
        var agora = Instant.now();
        var purposes = Arrays.stream(ContactPurpose.values())
                .map(p -> new PurposeView(p, p.basis().name(), c.allows(p, agora)))
                .toList();
        var history = c.consents().entries().stream()
                .map(e -> new ConsentEntryView(e.purpose(), e.decision().name(), e.at(), e.source()))
                .toList();
        return new ContactView(c.id(), c.customerId(), c.name().orElse(null), c.email().orElse(null),
                c.phone().orElse(null), c.role().orElse(null), c.isAnonymized(),
                c.anonymizedAt().orElse(null), purposes, history);
    }
}
