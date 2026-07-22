package br.com.brew.brassia.security.application.port.inbound;

import java.util.UUID;

public interface CompleteMfaLoginUseCase {
    Result handle(Command command);

    enum Method { TOTP, RECOVERY_CODE }

    record Command(UUID userId, String code, Method method) {}

    record Result(UUID userId, String displayName) {}
}
