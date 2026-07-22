package br.com.brew.brassia.security.adapter.inbound.web;

import br.com.brew.brassia.security.adapter.inbound.web.dto.ConfirmTotpRequest;
import br.com.brew.brassia.security.adapter.inbound.web.dto.DisableTotpRequest;
import br.com.brew.brassia.security.adapter.inbound.web.dto.EnrollTotpResponse;
import br.com.brew.brassia.security.adapter.inbound.web.dto.RecoveryCodesResponse;
import br.com.brew.brassia.security.application.port.inbound.ConfirmTotpUseCase;
import br.com.brew.brassia.security.application.port.inbound.DisableTotpUseCase;
import br.com.brew.brassia.security.application.port.inbound.EnrollTotpUseCase;
import br.com.brew.brassia.security.application.port.inbound.RegenerateRecoveryCodesUseCase;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/security")
final class MfaController {
    private final EnrollTotpUseCase enrollTotp;
    private final ConfirmTotpUseCase confirmTotp;
    private final DisableTotpUseCase disableTotp;
    private final RegenerateRecoveryCodesUseCase regenerateRecoveryCodes;

    MfaController(
            EnrollTotpUseCase enrollTotp,
            ConfirmTotpUseCase confirmTotp,
            DisableTotpUseCase disableTotp,
            RegenerateRecoveryCodesUseCase regenerateRecoveryCodes) {
        this.enrollTotp = enrollTotp;
        this.confirmTotp = confirmTotp;
        this.disableTotp = disableTotp;
        this.regenerateRecoveryCodes = regenerateRecoveryCodes;
    }

    @PostMapping("/totp/enroll")
    EnrollTotpResponse enroll(@AuthenticationPrincipal SecurityPrincipal principal) {
        var result = enrollTotp.handle(new EnrollTotpUseCase.Command(principal.userId()));
        return new EnrollTotpResponse(result.secret(), result.otpauthUri());
    }

    @PostMapping("/totp/confirm")
    ResponseEntity<Void> confirm(@Valid @RequestBody ConfirmTotpRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        confirmTotp.handle(new ConfirmTotpUseCase.Command(principal.userId(), request.code()));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/totp")
    ResponseEntity<Void> disable(@RequestBody(required = false) DisableTotpRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal,
            HttpServletRequest httpRequest) {
        var recentReauth = PendingMfaSession.hasRecentReauth(httpRequest.getSession(false));
        var password = request == null ? null : request.currentPassword();
        disableTotp.handle(new DisableTotpUseCase.Command(principal.userId(), password, recentReauth));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/recovery-codes/regenerate")
    RecoveryCodesResponse regenerate(@AuthenticationPrincipal SecurityPrincipal principal) {
        var result = regenerateRecoveryCodes.handle(new RegenerateRecoveryCodesUseCase.Command(principal.userId()));
        return new RecoveryCodesResponse(result.codes());
    }
}
