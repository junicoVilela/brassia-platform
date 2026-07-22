package br.com.brew.brassia.security.adapter.inbound.web;

import br.com.brew.brassia.security.adapter.inbound.web.dto.UpdateAlertStatusRequest;
import br.com.brew.brassia.security.application.port.inbound.ManageSecurityAlertUseCase;
import br.com.brew.brassia.security.application.port.outbound.SecurityAlertRepository;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/security/alerts")
final class SecurityAlertController {
    private final ManageSecurityAlertUseCase alerts;

    SecurityAlertController(ManageSecurityAlertUseCase alerts) {
        this.alerts = alerts;
    }

    @GetMapping
    List<SecurityAlertRepository.AlertView> list(
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("security.alert.read");
        return alerts.list(principal.requireBrewery(), status);
    }

    @PatchMapping("/{id}")
    void updateStatus(
            @PathVariable java.util.UUID id,
            @Valid @RequestBody UpdateAlertStatusRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("security.alert.manage");
        alerts.updateStatus(principal.requireBrewery(), principal.userId(), id, request.status());
    }
}
