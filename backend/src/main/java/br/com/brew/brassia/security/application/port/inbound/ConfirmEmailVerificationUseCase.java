package br.com.brew.brassia.security.application.port.inbound;

public interface ConfirmEmailVerificationUseCase {
    void handle(Command command);

    record Command(String rawToken) {}
}
