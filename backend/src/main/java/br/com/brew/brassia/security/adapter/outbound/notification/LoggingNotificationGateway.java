package br.com.brew.brassia.security.adapter.outbound.notification;

import br.com.brew.brassia.security.application.port.outbound.NotificationGateway;
import br.com.brew.brassia.security.domain.EmailAddress;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stub de notificação para esta sprint (sem SMTP). Registra que o convite foi
 * emitido; o token bruto só aparece em nível DEBUG (desligado em produção) para
 * permitir o fluxo local fim-a-fim. A entrega real chega em sprint futura.
 */
@Component
final class LoggingNotificationGateway implements NotificationGateway {
    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationGateway.class);

    @Override
    public void sendInvitation(EmailAddress email, String rawToken, Instant expiresAt) {
        log.info("Convite emitido para {} (expira em {})", email.normalized(), expiresAt);
        log.debug("Token de convite (somente dev) para {}: {}", email.normalized(), rawToken);
    }
}
