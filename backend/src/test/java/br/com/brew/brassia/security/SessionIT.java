package br.com.brew.brassia.security;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import br.com.brew.brassia.security.application.port.inbound.AcceptInvitationUseCase;
import br.com.brew.brassia.security.application.port.inbound.InviteUserUseCase;
import br.com.brew.brassia.security.application.port.outbound.NotificationGateway;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** SEC-006: histórico de login e gestão das próprias sessões. */
@SpringBootTest
@Testcontainers
class SessionIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired WebApplicationContext context;
    @Autowired InviteUserUseCase inviteUser;
    @Autowired AcceptInvitationUseCase acceptInvitation;
    @Autowired CapturingNotificationGateway capturedGateway;
    @Autowired JdbcTemplate jdbc;
    @Autowired FindByIndexNameSessionRepository<? extends Session> sessionRepository;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void loginRecordsHistoryWithoutPlaintext() throws Exception {
        var userId = onboard("hist@example.com", "segredo123");
        var session = login("hist@example.com", "segredo123");

        // Login com senha errada gera um FAILURE (identificador/IP só em hash).
        mockMvc.perform(post("/api/v1/security/login").with(csrf()).contentType("application/json")
                        .content("{\"email\":\"hist@example.com\",\"password\":\"errada000\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/security/login-events").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].outcome", hasItem("SUCCESS")));

        var failures = jdbc.queryForObject("SELECT count(*) FROM login_event WHERE outcome = 'FAILURE'", Integer.class);
        assertThatFailureIsHashed();
        org.assertj.core.api.Assertions.assertThat(failures).isGreaterThanOrEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(userId).isNotNull();
    }

    private void assertThatFailureIsHashed() {
        var plaintextIdentifier = jdbc.queryForObject(
                "SELECT count(*) FROM login_event WHERE attempted_identifier_hash = 'hist@example.com'", Integer.class);
        org.assertj.core.api.Assertions.assertThat(plaintextIdentifier).isZero();
    }

    @Test
    void listsAndRevokesOwnSessionsInIsolation() throws Exception {
        var userId = UUID.randomUUID();
        var other = UUID.randomUUID();
        var s1 = seedSession(userId);
        seedSession(userId);
        seedSession(other); // não deve aparecer

        var principal = authenticationFor(userId);

        mockMvc.perform(get("/api/v1/security/sessions").with(authentication(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        // Revoga uma pela ref (prefixo do id).
        mockMvc.perform(delete("/api/v1/security/sessions/" + s1.substring(0, 8))
                        .with(authentication(principal)).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/security/sessions").with(authentication(principal)))
                .andExpect(jsonPath("$", hasSize(1)));

        // Revoga as demais (nenhuma é a "atual" no MockMvc).
        mockMvc.perform(delete("/api/v1/security/sessions").with(authentication(principal)).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/security/sessions").with(authentication(principal)))
                .andExpect(jsonPath("$", hasSize(0)));

        // A sessão do outro usuário permanece intacta.
        org.assertj.core.api.Assertions.assertThat(sessionRepository.findByPrincipalName(other.toString())).hasSize(1);
    }

    private String seedSession(UUID userId) {
        var session = sessionRepository.createSession();
        session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, userId.toString());
        save(session);
        return session.getId();
    }

    @SuppressWarnings("unchecked")
    private <S extends Session> void save(Session session) {
        ((FindByIndexNameSessionRepository<S>) sessionRepository).save((S) session);
    }

    private Authentication authenticationFor(UUID userId) {
        var principal = new SecurityPrincipal(userId, UUID.randomUUID(), "User", Set.of());
        return new UsernamePasswordAuthenticationToken(principal, "n/a", Set.of());
    }

    private UUID onboard(String email, String password) throws Exception {
        var invite = inviteUser.handle(new InviteUserUseCase.Command(UUID.randomUUID(), UUID.randomUUID(), email, "Hist"));
        acceptInvitation.handle(new AcceptInvitationUseCase.Command(capturedGateway.lastRawToken, password));
        return invite.userId();
    }

    private MockHttpSession login(String email, String password) throws Exception {
        var result = mockMvc.perform(post("/api/v1/security/login").with(csrf()).contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    static final class CapturingNotificationGateway implements NotificationGateway {
        volatile String lastRawToken;
        @Override public void sendInvitation(br.com.brew.brassia.security.domain.EmailAddress email,
                String rawToken, Instant expiresAt) {
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
