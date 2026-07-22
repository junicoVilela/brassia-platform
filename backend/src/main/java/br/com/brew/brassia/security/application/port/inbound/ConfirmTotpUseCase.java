package br.com.brew.brassia.security.application.port.inbound;

import java.util.UUID;

public interface ConfirmTotpUseCase {
    void handle(Command command);

    record Command(UUID userId, String code) {}
}
