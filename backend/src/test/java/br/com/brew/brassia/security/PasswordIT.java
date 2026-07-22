package br.com.brew.brassia.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import br.com.brew.brassia.security.application.port.inbound.AcceptInvitationUseCase;
import br.com.brew.brassia.security.application.port.inbound.InviteUserUseCase;
import br.com.brew.brassia.security.application.port.outbound.NotificationGateway;
import br.com.brew.brassia.security.domain.EmailAddress;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Política e histórico de senha: troca, reuso, blocklist e login com a nova. */
@SpringBootTest
@Testcontainers
class PasswordIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired WebApplicationContext context;
    @Autowired InviteUserUseCase inviteUser;
    @Autowired AcceptInvitationUseCase acceptInvitation;
    @Autowired CapturingNotificationGateway capturedGateway;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void changePasswordEnforcesPolicyHistoryAndRelogin() throws Exception {
        onboard("pw-it@example.com", "segredo123");
        var session = login("pw-it@example.com", "segredo123");

        // Reusar a atual → 400.
        change(session, "segredo123", "segredo123").andExpect(status().isBadRequest());
        // Senha comprometida (blocklist) → 400.
        change(session, "segredo123", "password").andExpect(status().isBadRequest());
        // Senha atual incorreta → 400.
        change(session, "errada000", "novaSenha456").andExpect(status().isBadRequest());
        // Troca válida → 204.
        change(session, "segredo123", "novaSenha456").andExpect(status().isNoContent());

        // A antiga não loga mais; a nova sim.
        mockMvc.perform(post("/api/v1/security/login").with(csrf()).contentType("application/json")
                        .content("{\"email\":\"pw-it@example.com\",\"password\":\"segredo123\"}"))
                .andExpect(status().isUnauthorized());
        var newSession = login("pw-it@example.com", "novaSenha456");

        // Não pode voltar para a anterior (histórico).
        change(newSession, "novaSenha456", "segredo123").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("bad_request"));
    }

    private org.springframework.test.web.servlet.ResultActions change(MockHttpSession session,
            String current, String next) throws Exception {
        return mockMvc.perform(post("/api/v1/security/password/change").session(session).with(csrf())
                .contentType("application/json")
                .content("{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}"));
    }

    private void onboard(String email, String password) throws Exception {
        inviteUser.handle(new InviteUserUseCase.Command(UUID.randomUUID(), UUID.randomUUID(), email, "PW"));
        acceptInvitation.handle(new AcceptInvitationUseCase.Command(capturedGateway.lastRawToken, password));
    }

    private MockHttpSession login(String email, String password) throws Exception {
        var result = mockMvc.perform(post("/api/v1/security/login").with(csrf()).contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    static final class CapturingNotificationGateway implements NotificationGateway {
        volatile String lastRawToken;
        @Override public void sendInvitation(EmailAddress email, String rawToken, Instant expiresAt) {
            this.lastRawToken = rawToken;
        }
        @Override public void sendPasswordReset(EmailAddress email, String rawToken, Instant expiresAt) {
            this.lastRawToken = rawToken;
        }
        @Override public void sendEmailVerification(EmailAddress email, String rawToken, Instant expiresAt) {
            this.lastRawToken = rawToken;
        }
    }

    @TestConfiguration
    static class TestGatewayConfig {
        @Bean @Primary
        CapturingNotificationGateway capturingNotificationGateway() {
            return new CapturingNotificationGateway();
        }
    }
}
