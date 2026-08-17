package br.com.brew.brassia.sales.adapter.inbound.web;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.sales.application.port.outbound.PortalAccessRepository;
import br.com.brew.brassia.shared.money.Money;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Quem administra o portal, do lado da cervejaria (SAL-003).
 *
 * <p>Separado do próprio portal: conceder acesso e definir teto são atos de gestão, e quem os pratica
 * usa a plataforma interna. Alçada {@code sales.portal.manage}, crítica — dar acesso ao portal é abrir
 * a porta para alguém de fora, e definir o teto decide quanto a casa aceita carregar.
 */
@RestController
@RequestMapping("/api/v1/sales/portal")
final class PortalAdminController {

    private final PortalAccessRepository portal;
    private final AuditTrail audit;

    PortalAdminController(PortalAccessRepository portal, AuditTrail audit) {
        this.portal = Objects.requireNonNull(portal);
        this.audit = Objects.requireNonNull(audit);
    }

    @PutMapping("/access/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void grant(@AuthenticationPrincipal SecurityPrincipal principal, @PathVariable UUID userId,
            @Valid @RequestBody GrantRequest request) {
        principal.requirePermission("sales.portal.manage");
        var brewery = principal.requireBrewery();
        portal.grant(brewery, userId, request.customerId(), request.channelId(), principal.userId());
        audit.record(AuditEvent.success(brewery, principal.userId(), "sales.portal.grant",
                "sales.portal_user", userId.toString(),
                Map.of("customerId", request.customerId().toString(),
                        "channelId", request.channelId().toString())));
    }

    @DeleteMapping("/access/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(@AuthenticationPrincipal SecurityPrincipal principal, @PathVariable UUID userId) {
        principal.requirePermission("sales.portal.manage");
        var brewery = principal.requireBrewery();
        // Revogar apaga o vínculo, e não o usuário: ele continua existindo, com o histórico de acesso
        // que a auditoria guarda. O que some é o direito de ver o cliente.
        portal.revoke(brewery, userId);
        audit.record(AuditEvent.success(brewery, principal.userId(), "sales.portal.revoke",
                "sales.portal_user", userId.toString(), Map.of()));
    }

    @PutMapping("/credit/{customerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void setCredit(@AuthenticationPrincipal SecurityPrincipal principal, @PathVariable UUID customerId,
            @Valid @RequestBody CreditRequest request) {
        principal.requirePermission("sales.portal.manage");
        var brewery = principal.requireBrewery();
        // Passa pelo domínio para o teto não-positivo ser recusado num lugar só.
        Money.of(request.ceiling().toPlainString(), request.currency());
        portal.setCredit(brewery, customerId, request.ceiling(), request.currency(), principal.userId());
        audit.record(AuditEvent.success(brewery, principal.userId(), "sales.portal.set-credit",
                "crm.customer", customerId.toString(),
                Map.of("ceiling", request.ceiling().toPlainString(), "currency", request.currency())));
    }

    record GrantRequest(@NotNull UUID customerId, @NotNull UUID channelId) {}

    record CreditRequest(@NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal ceiling,
            @NotNull @Pattern(regexp = "[A-Z]{3}") String currency) {}
}
