package br.com.brew.brassia.production.adapter.inbound.web;

import br.com.brew.brassia.production.adapter.inbound.web.dto.AlertView;
import br.com.brew.brassia.production.adapter.inbound.web.dto.CreateAlertRequest;
import br.com.brew.brassia.production.application.port.inbound.ConfirmAlertUseCase;
import br.com.brew.brassia.production.application.port.inbound.CreateAlertUseCase;
import br.com.brew.brassia.production.application.port.inbound.ListAlertsUseCase;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Central de alertas/ações do lote (PRD-006): timeline persistida, confirmação idempotente. */
@RestController
@RequestMapping("/api/v1/production/batches/{batchId}/alerts")
final class AlertController {

    private final CreateAlertUseCase createAlert;
    private final ListAlertsUseCase listAlerts;
    private final ConfirmAlertUseCase confirmAlert;

    AlertController(CreateAlertUseCase createAlert, ListAlertsUseCase listAlerts, ConfirmAlertUseCase confirmAlert) {
        this.createAlert = createAlert;
        this.listAlerts = listAlerts;
        this.confirmAlert = confirmAlert;
    }

    @GetMapping
    List<AlertView> list(@PathVariable UUID batchId, @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("production.batch.read");
        return listAlerts.handle(principal.requireBrewery(), batchId).stream().map(AlertView::from).toList();
    }

    @PostMapping
    ResponseEntity<AlertView.Created> create(
            @PathVariable UUID batchId, @Valid @RequestBody CreateAlertRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("production.batch.manage");
        var result = createAlert.handle(new CreateAlertUseCase.Command(
                principal.userId(), principal.requireBrewery(), batchId, request.kind(), request.message(),
                request.plannedAt(), request.occurredAt()));
        return ResponseEntity.created(
                URI.create("/api/v1/production/batches/" + batchId + "/alerts/" + result.id()))
                .body(new AlertView.Created(result.id()));
    }

    @PostMapping("/{alertId}/confirm")
    AlertView confirm(@PathVariable UUID batchId, @PathVariable UUID alertId,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("production.batch.manage");
        return AlertView.from(confirmAlert.handle(new ConfirmAlertUseCase.Command(
                principal.userId(), principal.requireBrewery(), batchId, alertId)));
    }
}
