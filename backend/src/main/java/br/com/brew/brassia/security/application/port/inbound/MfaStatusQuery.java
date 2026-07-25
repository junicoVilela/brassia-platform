package br.com.brew.brassia.security.application.port.inbound;

import java.util.UUID;

/** Status de MFA da conta: se há TOTP ativo e quantos códigos de recuperação restam (SEC-B01). */
@FunctionalInterface
public interface MfaStatusQuery {
    Status of(UUID userId);

    record Status(boolean mfaEnabled, int recoveryCodesRemaining) {}
}
