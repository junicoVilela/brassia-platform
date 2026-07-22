package br.com.brew.brassia.security.application.port.inbound;

public interface RequestPasswordResetUseCase {
    void handle(Command command);

    record Command(String email) {}
}
