package br.com.brew.brassia.security.adapter.inbound.web;

import br.com.brew.brassia.brewery.BreweryRef;
import br.com.brew.brassia.security.adapter.inbound.web.dto.BreweryView;
import br.com.brew.brassia.security.adapter.inbound.web.dto.ChangePasswordRequest;
import br.com.brew.brassia.security.adapter.inbound.web.dto.LoginRequest;
import br.com.brew.brassia.security.adapter.inbound.web.dto.SessionResponse;
import br.com.brew.brassia.security.adapter.inbound.web.dto.SwitchBreweryRequest;
import br.com.brew.brassia.security.application.port.inbound.AuthenticateUserUseCase;
import br.com.brew.brassia.security.application.port.inbound.ChangePasswordUseCase;
import br.com.brew.brassia.security.application.port.inbound.LoginHistoryQuery;
import br.com.brew.brassia.security.application.port.inbound.RecordLoginAttemptUseCase;
import br.com.brew.brassia.security.application.port.inbound.ResolveSessionContextUseCase;
import br.com.brew.brassia.security.application.port.inbound.ResolveSessionContextUseCase.SessionContext;
import br.com.brew.brassia.security.domain.UserId;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import br.com.brew.brassia.shared.web.ProblemDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/security")
final class AuthenticationController {
    private final AuthenticateUserUseCase authenticate;
    private final ResolveSessionContextUseCase sessionContext;
    private final ChangePasswordUseCase changePassword;
    private final RecordLoginAttemptUseCase recordLoginAttempt;
    private final LoginHistoryQuery loginHistory;
    private final HttpSessionSecurityContextPersister sessionPersister;

    AuthenticationController(
            AuthenticateUserUseCase authenticate,
            ResolveSessionContextUseCase sessionContext,
            ChangePasswordUseCase changePassword,
            RecordLoginAttemptUseCase recordLoginAttempt,
            LoginHistoryQuery loginHistory,
            HttpSessionSecurityContextPersister sessionPersister) {
        this.authenticate = authenticate;
        this.sessionContext = sessionContext;
        this.changePassword = changePassword;
        this.recordLoginAttempt = recordLoginAttempt;
        this.loginHistory = loginHistory;
        this.sessionPersister = sessionPersister;
    }

    // Público: resolver o CsrfToken força a emissão do cookie XSRF-TOKEN.
    @GetMapping("/csrf")
    ResponseEntity<Void> csrf(CsrfToken token) {
        token.getToken();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    ResponseEntity<?> login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        AuthenticateUserUseCase.Result result;
        try {
            result = authenticate.handle(new AuthenticateUserUseCase.Command(request.email(), request.password()));
        } catch (IllegalArgumentException e) {
            recordLoginAttempt.record(new RecordLoginAttemptUseCase.Command(
                    null, request.email(), RecordLoginAttemptUseCase.Outcome.FAILURE,
                    "INVALID_CREDENTIALS", ip(httpRequest), userAgent(httpRequest), traceId()));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ProblemDetails.of(HttpStatus.UNAUTHORIZED, "invalid_credentials", "Credenciais inválidas."));
        }
        recordLoginAttempt.record(new RecordLoginAttemptUseCase.Command(
                result.userId(), request.email(), RecordLoginAttemptUseCase.Outcome.SUCCESS,
                "OK", ip(httpRequest), userAgent(httpRequest), traceId()));

        var context = sessionContext.resolve(new UserId(result.userId()), null);
        var principal = principal(result.userId(), result.displayName(), context);
        sessionPersister.persist(principal, httpRequest, httpResponse, true);
        return ResponseEntity.ok(toResponse(principal, context));
    }

    @PostMapping("/session/brewery")
    ResponseEntity<SessionResponse> switchBrewery(
            @Valid @RequestBody SwitchBreweryRequest request,
            @AuthenticationPrincipal SecurityPrincipal current,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        var context = sessionContext.resolve(new UserId(current.userId()), request.breweryId());
        var principal = principal(current.userId(), current.displayName(), context);
        sessionPersister.persist(principal, httpRequest, httpResponse, false);
        return ResponseEntity.ok(toResponse(principal, context));
    }

    @GetMapping("/login-events")
    List<LoginHistoryQuery.LoginEventView> loginEvents(@AuthenticationPrincipal SecurityPrincipal principal) {
        return loginHistory.recentByUser(principal.userId(), 50);
    }

    @GetMapping("/session")
    SessionResponse session(@AuthenticationPrincipal SecurityPrincipal principal) {
        // Reresolve para expor as cervejarias acessíveis (não guardadas no principal).
        var context = sessionContext.resolve(new UserId(principal.userId()), principal.breweryId());
        return toResponse(principal, context);
    }

    @PostMapping("/password/change")
    ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        changePassword.handle(new ChangePasswordUseCase.Command(
                principal.userId(), request.currentPassword(), request.newPassword()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest httpRequest) {
        sessionPersister.clear(httpRequest);
        return ResponseEntity.noContent().build();
    }

    private static String ip(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    private static String userAgent(HttpServletRequest request) {
        return request.getHeader("User-Agent");
    }

    private static String traceId() {
        return ProblemDetails.currentTraceId();
    }

    private static SecurityPrincipal principal(java.util.UUID userId, String displayName, SessionContext context) {
        return new SecurityPrincipal(userId, context.activeBreweryId(), displayName, context.permissions());
    }

    private static SessionResponse toResponse(SecurityPrincipal principal, SessionContext context) {
        var accessible = context.accessibleBreweries().stream().map(AuthenticationController::view).toList();
        var active = context.accessibleBreweries().stream()
                .filter(b -> b.id().equals(context.activeBreweryId()))
                .findFirst().map(AuthenticationController::view).orElse(null);
        return new SessionResponse(principal.userId(), principal.displayName(), active, accessible, principal.permissions());
    }

    private static BreweryView view(BreweryRef ref) {
        return new BreweryView(ref.id(), ref.code(), ref.name());
    }
}
