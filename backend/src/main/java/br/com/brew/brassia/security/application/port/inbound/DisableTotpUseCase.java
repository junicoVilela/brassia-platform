package br.com.brew.brassia.security.application.port.inbound;

import java.util.UUID;

public interface DisableTotpUseCase {
    void handle(Command command);

    record Command(UUID userId, String currentPassword, boolean recentReauth) {}
}
