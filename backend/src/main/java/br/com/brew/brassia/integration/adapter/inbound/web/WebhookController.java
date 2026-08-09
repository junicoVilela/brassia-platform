package br.com.brew.brassia.integration.adapter.inbound.web;

import br.com.brew.brassia.integration.adapter.inbound.web.dto.WebhookDtos;
import br.com.brew.brassia.integration.application.port.inbound.SubscriptionCommands;
import br.com.brew.brassia.integration.application.port.inbound.SubscriptionQueries;
import br.com.brew.brassia.integration.domain.SubscriptionStatus;
import br.com.brew.brassia.integration.domain.WebhookEventType;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Webhooks vistos de fora (INT-002). */
@RestController
@RequestMapping("/api/v1/integration/webhooks")
final class WebhookController {

    private static final int DEFAULT_LIMIT = 50;

    private final SubscriptionCommands commands;
    private final SubscriptionQueries queries;

    WebhookController(SubscriptionCommands commands, SubscriptionQueries queries) {
        this.commands = commands;
        this.queries = queries;
    }

    /** Os tipos que podem ser assinados. A allowlist é fechada e a tela a lê daqui. */
    @GetMapping("/event-types")
    Set<String> eventTypes(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("integration.webhook.read");
        return WebhookEventType.externalNames();
    }

    @GetMapping
    List<WebhookDtos.SubscriptionView> subscriptions(
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("integration.webhook.read");
        return WebhookDtos.SubscriptionView.from(queries.subscriptions(principal.requireBrewery()));
    }

    /**
     * Cria a assinatura e devolve o segredo uma única vez.
     *
     * <p>Criar é a operação crítica desta história — ela aponta um fluxo de dados da cervejaria para um
     * endereço de fora. Por isso exige {@code integration.webhook.manage}, enquanto pausar e revogar, que
     * <em>interrompem</em> esse fluxo, exigem apenas leitura: uma alçada difícil para "parar de mandar"
     * produziria o incentivo errado no momento em que se descobre que o destino foi comprometido.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    WebhookDtos.CreatedView create(@AuthenticationPrincipal SecurityPrincipal principal,
            @Valid @RequestBody WebhookDtos.CreateRequest request) {
        principal.requirePermission("integration.webhook.manage");
        return WebhookDtos.CreatedView.from(commands.create(new SubscriptionCommands.CreateRequest(
                principal.userId(), principal.requireBrewery(), request.name(), request.endpoint(),
                request.events())));
    }

    @PostMapping("/{subscriptionId}/status")
    WebhookDtos.SubscriptionView changeStatus(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID subscriptionId,
            @Valid @RequestBody WebhookDtos.ChangeStatusRequest request) {
        var target = parseStatus(request.status());
        // Reativar volta a mandar dados para fora, então tem a alçada de criar. Pausar e revogar, não.
        principal.requirePermission(target == SubscriptionStatus.ACTIVE
                ? "integration.webhook.manage" : "integration.webhook.read");
        return WebhookDtos.SubscriptionView.from(
                commands.changeStatus(new SubscriptionCommands.ChangeStatusRequest(
                        principal.userId(), principal.requireBrewery(), subscriptionId, target,
                        request.expectedVersion())));
    }

    /** As entregas recentes: é onde se vê o que falhou, quantas vezes e por quê. */
    @GetMapping("/{subscriptionId}/deliveries")
    List<WebhookDtos.DeliveryView> deliveries(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID subscriptionId,
            @RequestParam(required = false) Integer limit) {
        principal.requirePermission("integration.webhook.read");
        return WebhookDtos.DeliveryView.from(queries.deliveries(principal.requireBrewery(),
                subscriptionId, limit == null ? DEFAULT_LIMIT : limit));
    }

    private static SubscriptionStatus parseStatus(String raw) {
        try {
            return SubscriptionStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("estado inválido: " + raw);
        }
    }
}
