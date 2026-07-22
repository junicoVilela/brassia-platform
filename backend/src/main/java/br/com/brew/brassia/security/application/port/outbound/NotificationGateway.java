package br.com.brew.brassia.security.application.port.outbound;

import br.com.brew.brassia.security.domain.EmailAddress;
import java.time.Instant;

/**
 * Canal de saída para notificações de conta. Nesta sprint há apenas um stub que
 * registra em log; a entrega real (SMTP/provedor) chega em sprint futura.
 */
public interface NotificationGateway {
    void sendInvitation(EmailAddress email, String rawToken, Instant expiresAt);
    void sendPasswordReset(EmailAddress email, String rawToken, Instant expiresAt);
    void sendEmailVerification(EmailAddress email, String rawToken, Instant expiresAt);
}
