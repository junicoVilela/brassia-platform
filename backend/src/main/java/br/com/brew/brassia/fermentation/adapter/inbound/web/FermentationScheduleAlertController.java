package br.com.brew.brassia.fermentation.adapter.inbound.web;

import br.com.brew.brassia.fermentation.application.port.inbound.RaiseLateStepAlertsUseCase;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Alertas de etapa atrasada da agenda (FER-004). Abre item na central do lote (PRD-006):
 * é aviso, e nunca altera setpoint, equipamento ou o estado da etapa.
 */
@RestController
final class FermentationScheduleAlertController {

    private final RaiseLateStepAlertsUseCase raiseAlerts;

    FermentationScheduleAlertController(RaiseLateStepAlertsUseCase raiseAlerts) {
        this.raiseAlerts = raiseAlerts;
    }

    @PostMapping("/api/v1/fermentation/schedule/late-step-alerts")
    List<UUID> raiseLateAlerts(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("fermentation.schedule.manage");
        return raiseAlerts.handle(principal.userId(), principal.requireBrewery());
    }
}
