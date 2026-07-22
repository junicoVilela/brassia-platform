package br.com.brew.brassia.security.adapter.inbound.web;

import br.com.brew.brassia.security.application.port.inbound.LoginHistoryQuery;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Consulta do próprio histórico de eventos de login (SEC-006, self-service). */
@RestController
@RequestMapping("/api/v1/security/login-events")
final class LoginHistoryController {
    private static final int RECENT_LIMIT = 50;

    private final LoginHistoryQuery loginHistory;

    LoginHistoryController(LoginHistoryQuery loginHistory) {
        this.loginHistory = loginHistory;
    }

    @GetMapping
    List<LoginHistoryQuery.LoginEventView> recent(@AuthenticationPrincipal SecurityPrincipal principal) {
        return loginHistory.recentByUser(principal.userId(), RECENT_LIMIT);
    }
}
