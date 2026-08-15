package br.com.brew.brassia.crm.adapter.inbound.web;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.crm.adapter.inbound.web.dto.CrmDtos.RetentionPolicyView;
import br.com.brew.brassia.crm.adapter.inbound.web.dto.CrmDtos.SetRetentionRequest;
import br.com.brew.brassia.crm.application.port.inbound.CustomerCommands;
import br.com.brew.brassia.crm.application.port.outbound.RetentionPolicyRepository;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.Objects;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Por quanto tempo a casa guarda dado pessoal (CRM-001).
 *
 * <p>Endpoint separado do cadastro, com permissão crítica própria: definir prazo de retenção é decisão
 * de gestão sobre dado pessoal, e não o mesmo ato de cadastrar um cliente. Quem atende o balcão não
 * deveria conseguir encurtar o prazo de guarda da base inteira.
 */
@RestController
@RequestMapping("/api/v1/crm/retention-policy")
final class RetentionPolicyController {

    private final CustomerCommands commands;
    private final RetentionPolicyRepository policies;
    private final AuditTrail audit;

    RetentionPolicyController(CustomerCommands commands, RetentionPolicyRepository policies,
            AuditTrail audit) {
        this.commands = Objects.requireNonNull(commands);
        this.policies = Objects.requireNonNull(policies);
        this.audit = Objects.requireNonNull(audit);
    }

    @GetMapping
    RetentionPolicyView get(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("crm.customer.read");
        // Nulo é resposta legítima e significa "a casa não decidiu" — e, enquanto não decidir, nada
        // expira. A tela mostra isso como lacuna, não como zero.
        return new RetentionPolicyView(policies.find(principal.requireBrewery())
                .daysAfterLastInteraction().orElse(null));
    }

    @PutMapping
    RetentionPolicyView save(@AuthenticationPrincipal SecurityPrincipal principal,
            @Valid @RequestBody SetRetentionRequest request) {
        principal.requirePermission("crm.retention.manage");
        var brewery = principal.requireBrewery();
        commands.setRetentionDays(brewery, principal.userId(), request.daysAfterLastInteraction());
        audit.record(AuditEvent.success(brewery, principal.userId(), "crm.retention.save",
                "crm.retention_policy", brewery.toString(),
                Map.of("days", String.valueOf(request.daysAfterLastInteraction()))));
        return new RetentionPolicyView(request.daysAfterLastInteraction());
    }
}
