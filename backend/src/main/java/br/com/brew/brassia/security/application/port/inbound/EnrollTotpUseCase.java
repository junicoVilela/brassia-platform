package br.com.brew.brassia.security.application.port.inbound;

import java.util.List;
import java.util.UUID;

public interface EnrollTotpUseCase {
    Result handle(Command command);

    record Command(UUID userId) {}

    record Result(String secret, String otpauthUri) {}
}
