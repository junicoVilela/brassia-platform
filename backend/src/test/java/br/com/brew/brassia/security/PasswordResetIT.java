package br.com.brew.brassia.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import br.com.brew.brassia.security.application.port.inbound.AcceptInvitationUseCase;
import br.com.brew.brassia.security.application.port.inbound.InviteUserUseCase;
import br.com.brew.brassia.security.application.port.outbound.NotificationGateway;
import br.com.brew.brassia.security.domain.EmailAddress;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class PasswordResetIT {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired WebApplicationContext context;
    @Autowired InviteUserUseCase inviteUser;
    @Autowired AcceptInvitationUseCase acceptInvitation;
    @Autowired CapturingNotificationGateway gateway;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void unknownEmailReturnsSameResponse() throws Exception {
        mockMvc.perform(post("/api/v1/security/password/forgot").with(csrf())
                        .contentType("application/json").content("{\"email\":\"nobody@example.com\"}"))
                .andExpect(status().isAccepted());
    }

    @Test
    void validResetChangesPassword() throws Exception {
        inviteUser.handle(new InviteUserUseCase.Command(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                "reset@example.com", "Reset"));
        acceptInvitation.handle(new AcceptInvitationUseCase.Command(gateway.lastRawToken, "oldpass123"));

        mockMvc.perform(post("/api/v1/security/password/forgot").with(csrf())
                        .contentType("application/json").content("{\"email\":\"reset@example.com\"}"))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/v1/security/password/reset").with(csrf())
                        .contentType("application/json")
                        .content("{\"token\":\"" + gateway.lastRawToken + "\",\"newPassword\":\"newpass456\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/security/login").with(csrf()).contentType("application/json")
                        .content("{\"email\":\"reset@example.com\",\"password\":\"newpass456\"}"))
                .andExpect(status().isOk());
    }

    static final class CapturingNotificationGateway implements NotificationGateway {
        volatile String lastRawToken;
        @Override public void sendInvitation(EmailAddress email, String rawToken, Instant expiresAt) { lastRawToken = rawToken; }
        @Override public void sendPasswordReset(EmailAddress email, String rawToken, Instant expiresAt) { lastRawToken = rawToken; }
        @Override public void sendEmailVerification(EmailAddress email, String rawToken, Instant expiresAt) { lastRawToken = rawToken; }
    }

    @TestConfiguration
    static class Config {
        @Bean @Primary CapturingNotificationGateway capturingNotificationGateway() { return new CapturingNotificationGateway(); }
    }
}
