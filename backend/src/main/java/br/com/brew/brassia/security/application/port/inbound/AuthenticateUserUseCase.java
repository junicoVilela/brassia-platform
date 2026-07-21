package br.com.brew.brassia.security.application.port.inbound;

import java.util.Set;
import java.util.UUID;

@FunctionalInterface
public interface AuthenticateUserUseCase {
    Result handle(Command command);

    record Command(String email, String password) {}

    record Result(UUID userId, String displayName, String email, Set<String> permissions) {}
}
