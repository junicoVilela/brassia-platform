package br.com.brew.brassia.security.application.port.inbound;

import java.util.UUID;

@FunctionalInterface
public interface ChangePasswordUseCase {
    void handle(Command command);

    record Command(UUID userId, String currentPassword, String newPassword) {}
}
