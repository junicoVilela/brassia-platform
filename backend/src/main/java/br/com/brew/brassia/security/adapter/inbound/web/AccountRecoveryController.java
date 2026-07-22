package br.com.brew.brassia.security.adapter.inbound.web;

import br.com.brew.brassia.security.adapter.inbound.web.dto.EmailVerificationRequest;
import br.com.brew.brassia.security.adapter.inbound.web.dto.ForgotPasswordRequest;
import br.com.brew.brassia.security.adapter.inbound.web.dto.ResetPasswordRequest;
import br.com.brew.brassia.security.application.port.inbound.ConfirmEmailVerificationUseCase;
import br.com.brew.brassia.security.application.port.inbound.RequestEmailVerificationUseCase;
import br.com.brew.brassia.security.application.port.inbound.RequestPasswordResetUseCase;
import br.com.brew.brassia.security.application.port.inbound.ResetPasswordUseCase;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/security")
final class AccountRecoveryController {
    private final RequestPasswordResetUseCase requestPasswordReset;
    private final ResetPasswordUseCase resetPassword;
    private final RequestEmailVerificationUseCase requestEmailVerification;
    private final ConfirmEmailVerificationUseCase confirmEmailVerification;

    AccountRecoveryController(
            RequestPasswordResetUseCase requestPasswordReset,
            ResetPasswordUseCase resetPassword,
            RequestEmailVerificationUseCase requestEmailVerification,
            ConfirmEmailVerificationUseCase confirmEmailVerification) {
        this.requestPasswordReset = requestPasswordReset;
        this.resetPassword = resetPassword;
        this.requestEmailVerification = requestEmailVerification;
        this.confirmEmailVerification = confirmEmailVerification;
    }

    @PostMapping("/password/forgot")
    ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        requestPasswordReset.handle(new RequestPasswordResetUseCase.Command(request.email()));
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/password/reset")
    ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        resetPassword.handle(new ResetPasswordUseCase.Command(request.token(), request.newPassword()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/email-verification/request")
    ResponseEntity<Void> requestVerification(@AuthenticationPrincipal SecurityPrincipal principal) {
        requestEmailVerification.handle(new RequestEmailVerificationUseCase.Command(principal.userId()));
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/email-verification/confirm")
    ResponseEntity<Void> confirmVerification(@Valid @RequestBody EmailVerificationRequest request) {
        confirmEmailVerification.handle(new ConfirmEmailVerificationUseCase.Command(request.token()));
        return ResponseEntity.noContent().build();
    }
}
