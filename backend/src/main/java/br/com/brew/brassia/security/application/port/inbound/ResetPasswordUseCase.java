package br.com.brew.brassia.security.application.port.inbound;

public interface ResetPasswordUseCase {
    void handle(Command command);

    record Command(String rawToken, String newPassword) {}
}
