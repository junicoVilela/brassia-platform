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
import br.com.brew.brassia.security.domain.Totp;
import java.time.Instant;
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

@SpringBootTest
@Testcontainers
class MfaIT {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired WebApplicationContext context;
    @Autowired InviteUserUseCase inviteUser;
    @Autowired AcceptInvitationUseCase acceptInvitation;
    @Autowired CapturingNotificationGateway gateway;
    MockMvc mockMvc;
    volatile String enrolledSecret;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void enrollConfirmAndLoginRequiresMfa() throws Exception {
        onboard("mfa@example.com", "segredo123");
        var session = login("mfa@example.com", "segredo123");
        var enroll = mockMvc.perform(post("/api/v1/security/totp/enroll").session(session).with(csrf()))
                .andExpect(status().isOk()).andReturn();
        enrolledSecret = com.jayway.jsonpath.JsonPath.read(enroll.getResponse().getContentAsString(), "$.secret");
        var code = Totp.currentCode(enrolledSecret, System.currentTimeMillis() / 1000L);
        mockMvc.perform(post("/api/v1/security/totp/confirm").session(session).with(csrf())
                        .contentType("application/json").content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/security/login").with(csrf()).contentType("application/json")
                        .content("{\"email\":\"mfa@example.com\",\"password\":\"segredo123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MFA_REQUIRED"));
    }

    @Test
    void wrongMfaCodeFails() throws Exception {
        onboard("mfa2@example.com", "segredo123");
        enableMfa("mfa2@example.com", "segredo123");
        var pre = mockMvc.perform(post("/api/v1/security/login").with(csrf()).contentType("application/json")
                        .content("{\"email\":\"mfa2@example.com\",\"password\":\"segredo123\"}"))
                .andExpect(status().isOk()).andReturn();
        var session = (MockHttpSession) pre.getRequest().getSession(false);
        mockMvc.perform(post("/api/v1/security/login/mfa").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"000000\",\"method\":\"TOTP\"}"))
                .andExpect(status().isUnauthorized());
    }

    private void onboard(String email, String password) throws Exception {
        inviteUser.handle(new InviteUserUseCase.Command(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), email, "MFA"));
        acceptInvitation.handle(new AcceptInvitationUseCase.Command(gateway.lastRawToken, password));
    }

    private MockHttpSession login(String email, String password) throws Exception {
        var result = mockMvc.perform(post("/api/v1/security/login").with(csrf()).contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private void enableMfa(String email, String password) throws Exception {
        var session = login(email, password);
        var body = mockMvc.perform(post("/api/v1/security/totp/enroll").session(session).with(csrf()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        enrolledSecret = com.jayway.jsonpath.JsonPath.read(body, "$.secret");
        var code = Totp.currentCode(enrolledSecret, System.currentTimeMillis() / 1000L);
        mockMvc.perform(post("/api/v1/security/totp/confirm").session(session).with(csrf())
                        .contentType("application/json").content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isNoContent());
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
