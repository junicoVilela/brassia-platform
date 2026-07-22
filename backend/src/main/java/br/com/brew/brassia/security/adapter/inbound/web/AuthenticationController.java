package br.com.brew.brassia.security.adapter.inbound.web;

import br.com.brew.brassia.security.adapter.inbound.web.dto.ChangePasswordRequest;
import br.com.brew.brassia.security.adapter.inbound.web.dto.LoginRequest;
import br.com.brew.brassia.security.adapter.inbound.web.dto.LoginResponse;
import br.com.brew.brassia.security.adapter.inbound.web.dto.SessionResponse;
import br.com.brew.brassia.security.adapter.inbound.web.dto.SwitchBreweryRequest;
import br.com.brew.brassia.security.adapter.inbound.web.dto.MfaLoginRequest;
import br.com.brew.brassia.security.adapter.inbound.web.dto.MfaRequiredResponse;
import br.com.brew.brassia.security.application.port.inbound.CompleteMfaLoginUseCase;
import br.com.brew.brassia.security.application.port.inbound.ChangePasswordUseCase;
import br.com.brew.brassia.security.application.port.inbound.PerformLoginUseCase;
import br.com.brew.brassia.security.application.port.inbound.ResolveSessionContextUseCase;
import br.com.brew.brassia.security.application.port.inbound.ResolveSessionContextUseCase.SessionContext;
import br.com.brew.brassia.security.domain.UserId;
import br.com.brew.brassia.shared.security.InvalidMfaException;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import br.com.brew.brassia.shared.web.ProblemDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
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
    private final PerformLoginUseCase performLogin;
    private final CompleteMfaLoginUseCase completeMfaLogin;
    private final ResolveSessionContextUseCase sessionContext;
    private final ChangePasswordUseCase changePassword;
    private final HttpSessionSecurityContextPersister sessionPersister;

    AuthenticationController(
            PerformLoginUseCase performLogin,
            CompleteMfaLoginUseCase completeMfaLogin,
            ResolveSessionContextUseCase sessionContext,
            ChangePasswordUseCase changePassword,
            HttpSessionSecurityContextPersister sessionPersister) {
        this.performLogin = performLogin;
        this.completeMfaLogin = completeMfaLogin;
        this.sessionContext = sessionContext;
        this.changePassword = changePassword;
        this.sessionPersister = sessionPersister;
    }

    // Público: resolver o CsrfToken força a emissão do cookie XSRF-TOKEN.
    @GetMapping("/csrf")
    ResponseEntity<Void> csrf(CsrfToken token) {
        token.getToken();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        // Limite, autenticação e histórico ficam no caso de uso; aqui só a borda HTTP.
        // TooManyRequestsException e InvalidCredentialsException viram Problem Details
        // (429/401) no ApiExceptionHandler.
        var result = performLogin.handle(new PerformLoginUseCase.Command(
                request.email(), request.password(), ip(httpRequest), userAgent(httpRequest), traceId()));

        if (result.mfaRequired()) {
            var session = httpRequest.getSession(true);
            PendingMfaSession.store(session, result.userId(), result.displayName());
            return ResponseEntity.ok(MfaRequiredResponse.totp());
        }

        var context = sessionContext.resolve(new UserId(result.userId()), null);
        var principal = principal(result.userId(), result.displayName(), context);
        sessionPersister.persist(principal, httpRequest, httpResponse, true);
        return ResponseEntity.ok(SessionResponse.from(principal, context));
    }

    @PostMapping("/login/mfa")
    SessionResponse completeMfa(@Valid @RequestBody MfaLoginRequest request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        var session = httpRequest.getSession(false);
        if (session == null) {
            throw new InvalidMfaException("Sessão MFA inválida.");
        }
        var userId = PendingMfaSession.requireUserId(session);
        CompleteMfaLoginUseCase.Result result;
        try {
            result = completeMfaLogin.handle(new CompleteMfaLoginUseCase.Command(
                    userId, request.code(), CompleteMfaLoginUseCase.Method.valueOf(request.method())));
        } catch (IllegalArgumentException e) {
            throw new InvalidMfaException("Código inválido.");
        }
        PendingMfaSession.clear(session);
        var context = sessionContext.resolve(new UserId(result.userId()), null);
        var principal = principal(result.userId(), result.displayName(), context);
        sessionPersister.persist(principal, httpRequest, httpResponse, true);
        return SessionResponse.from(principal, context);
    }

    @PostMapping("/session/brewery")
    ResponseEntity<SessionResponse> switchBrewery(
            @Valid @RequestBody SwitchBreweryRequest request,
            @AuthenticationPrincipal SecurityPrincipal current,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        var context = sessionContext.resolve(new UserId(current.userId()), request.breweryId());
        var principal = principal(current.userId(), current.displayName(), context);
        sessionPersister.persist(principal, httpRequest, httpResponse, false);
        return ResponseEntity.ok(SessionResponse.from(principal, context));
    }

    @GetMapping("/session")
    SessionResponse session(@AuthenticationPrincipal SecurityPrincipal principal) {
        // Reresolve para expor as cervejarias acessíveis (não guardadas no principal).
        var context = sessionContext.resolve(new UserId(principal.userId()), principal.breweryId());
        return SessionResponse.from(principal, context);
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
}
