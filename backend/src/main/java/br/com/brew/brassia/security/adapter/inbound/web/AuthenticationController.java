package br.com.brew.brassia.security.adapter.inbound.web;

import br.com.brew.brassia.security.application.port.inbound.AuthenticateUserUseCase;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import br.com.brew.brassia.shared.web.ProblemDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/security")
final class AuthenticationController {
    private final AuthenticateUserUseCase authenticate;
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();
    private final SecurityContextHolderStrategy holder = SecurityContextHolder.getContextHolderStrategy();

    AuthenticationController(AuthenticateUserUseCase authenticate) {
        this.authenticate = authenticate;
    }

    @PostMapping("/login")
    ResponseEntity<?> login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        AuthenticateUserUseCase.Result result;
        try {
            result = authenticate.handle(new AuthenticateUserUseCase.Command(request.email(), request.password()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ProblemDetails.of(HttpStatus.UNAUTHORIZED, "invalid_credentials", "Credenciais inválidas."));
        }

        var principal = SecurityPrincipal.identityOnly(result.userId(), result.displayName());
        var context = holder.createEmptyContext();
        context.setAuthentication(new SecurityPrincipalAuthentication(principal));
        holder.setContext(context);

        // Rotaciona o identificador da sessão (proteção contra fixation) e persiste o contexto.
        httpRequest.getSession(true);
        httpRequest.changeSessionId();
        contextRepository.saveContext(context, httpRequest, httpResponse);

        return ResponseEntity.ok(toResponse(principal));
    }

    @GetMapping("/session")
    SessionResponse session(@AuthenticationPrincipal SecurityPrincipal principal) {
        return toResponse(principal);
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest httpRequest) {
        var session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        holder.clearContext();
        return ResponseEntity.noContent().build();
    }

    private static SessionResponse toResponse(SecurityPrincipal principal) {
        return new SessionResponse(principal.userId(), principal.displayName(),
                principal.breweryId(), principal.permissions());
    }

    record LoginRequest(@NotBlank String email, @NotBlank String password) {}

    record SessionResponse(UUID userId, String displayName, UUID brewery, Set<String> permissions) {}
}
