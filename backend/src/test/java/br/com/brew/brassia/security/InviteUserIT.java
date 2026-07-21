package br.com.brew.brassia.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import br.com.brew.brassia.security.application.port.inbound.InviteUserUseCase;
import br.com.brew.brassia.security.application.port.inbound.InviteUserUseCase.Command;
import br.com.brew.brassia.security.application.port.outbound.NotificationGateway;
import br.com.brew.brassia.security.domain.EmailAddress;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integração do convite de usuário contra PostgreSQL 18 real (Testcontainers):
 * persistência + unicidade e autorização do endpoint (403 sem permissão, 201 com).
 */
@SpringBootTest
@Testcontainers
class InviteUserIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired InviteUserUseCase inviteUser;
    @Autowired JdbcTemplate jdbc;
    @Autowired WebApplicationContext context;
    @Autowired CapturingNotificationGateway capturedGateway;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void persistsInvitedUserAndTokenThenRejectsDuplicate() {
        var command = new Command(UUID.randomUUID(), UUID.randomUUID(), "it-user@example.com", "IT User");

        var result = inviteUser.handle(command);

        assertThat(result.status()).isEqualTo("INVITED");
        assertThat(count("security_user", "normalized_email = 'it-user@example.com' AND status = 'INVITED'")).isEqualTo(1);
        assertThat(count("account_token", "user_id = '" + result.userId() + "' AND token_type = 'INVITATION'")).isEqualTo(1);

        assertThatThrownBy(() -> inviteUser.handle(command)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void endpointDeniesWithoutPermission() throws Exception {
        mockMvc.perform(post("/api/v1/security/users")
                        .with(authentication(principal(Set.of())))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"email\":\"denied@example.com\",\"displayName\":\"Denied\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));

        assertThat(count("security_user", "normalized_email = 'denied@example.com'")).isZero();
    }

    @Test
    void endpointInvitesWithPermission() throws Exception {
        mockMvc.perform(post("/api/v1/security/users")
                        .with(authentication(principal(Set.of("security.user.invite"))))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"email\":\"allowed@example.com\",\"displayName\":\"Allowed\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("INVITED"))
                .andExpect(jsonPath("$.email").value("allowed@example.com"));

        assertThat(count("security_user", "normalized_email = 'allowed@example.com' AND status = 'INVITED'")).isEqualTo(1);
    }

    @Test
    void acceptInvitationActivatesAccountThenRejectsReuse() throws Exception {
        inviteUser.handle(new Command(UUID.randomUUID(), UUID.randomUUID(), "accept-it@example.com", "Accept IT"));
        var rawToken = capturedGateway.lastRawToken;
        assertThat(rawToken).isNotBlank();

        mockMvc.perform(post("/api/v1/security/users/accept-invitation")
                        .contentType("application/json")
                        .content("{\"token\":\"" + rawToken + "\",\"password\":\"segredo123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(count("security_user", "normalized_email = 'accept-it@example.com' AND status = 'ACTIVE'")).isEqualTo(1);

        // Token de uso único: a segunda tentativa é rejeitada com mensagem genérica.
        mockMvc.perform(post("/api/v1/security/users/accept-invitation")
                        .contentType("application/json")
                        .content("{\"token\":\"" + rawToken + "\",\"password\":\"segredo123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("bad_request"));
    }

    @Test
    void adminBlocksUnblocksAndDisablesAccount() throws Exception {
        var invite = inviteUser.handle(new Command(UUID.randomUUID(), UUID.randomUUID(), "admin-target@example.com", "Target"));
        var rawToken = capturedGateway.lastRawToken;
        mockMvc.perform(post("/api/v1/security/users/accept-invitation")
                        .contentType("application/json").content("{\"token\":\"" + rawToken + "\",\"password\":\"segredo123\"}"))
                .andExpect(status().isOk());
        var id = invite.userId();

        perform(id, "block", Set.of("security.user.block")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOCKED"));
        perform(id, "unblock", Set.of("security.user.block")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        perform(id, "disable", Set.of("security.user.disable")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));

        assertThat(count("security_user", "id = '" + id + "' AND status = 'DISABLED'")).isEqualTo(1);
    }

    @Test
    void listReturnsInvitedUserWithPermissionAndDeniesWithout() throws Exception {
        inviteUser.handle(new Command(UUID.randomUUID(), UUID.randomUUID(), "list-it@example.com", "List IT"));

        mockMvc.perform(get("/api/v1/security/users")
                        .with(authentication(principal(Set.of("security.user.read")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.email == 'list-it@example.com')].status").value(org.hamcrest.Matchers.hasItem("INVITED")))
                .andExpect(jsonPath("$.totalElements").isNumber());

        mockMvc.perform(get("/api/v1/security/users")
                        .with(authentication(principal(Set.of()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
    }

    @Test
    void adminOperationDeniedWithoutPermission() throws Exception {
        perform(UUID.randomUUID(), "block", Set.of())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
    }

    @Test
    void loginThenSessionThenLogoutFlow() throws Exception {
        onboard("login-it@example.com", "segredo123");

        var loginResult = mockMvc.perform(post("/api/v1/security/login")
                        .with(csrf()).contentType("application/json")
                        .content("{\"email\":\"login-it@example.com\",\"password\":\"segredo123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").isNotEmpty())
                .andExpect(jsonPath("$.permissions").isEmpty())
                .andReturn();
        var session = (org.springframework.mock.web.MockHttpSession) loginResult.getRequest().getSession(false);

        mockMvc.perform(get("/api/v1/security/session").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("User"));

        mockMvc.perform(post("/api/v1/security/logout").with(csrf()).session(session))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/security/session").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginRejectsWrongPassword() throws Exception {
        onboard("wrongpass-it@example.com", "segredo123");

        mockMvc.perform(post("/api/v1/security/login")
                        .with(csrf()).contentType("application/json")
                        .content("{\"email\":\"wrongpass-it@example.com\",\"password\":\"errada00\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_credentials"));
    }

    /** Onboarding completo: convida e aceita (define a senha) via endpoint público. */
    private void onboard(String email, String password) throws Exception {
        inviteUser.handle(new Command(UUID.randomUUID(), UUID.randomUUID(), email, "User"));
        var rawToken = capturedGateway.lastRawToken;
        mockMvc.perform(post("/api/v1/security/users/accept-invitation")
                        .contentType("application/json")
                        .content("{\"token\":\"" + rawToken + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions perform(UUID userId, String action, Set<String> permissions) throws Exception {
        return mockMvc.perform(post("/api/v1/security/users/" + userId + "/" + action)
                .with(authentication(principal(permissions)))
                .with(csrf()));
    }

    private Authentication principal(Set<String> permissions) {
        var securityPrincipal = new SecurityPrincipal(UUID.randomUUID(), UUID.randomUUID(), "Admin", permissions);
        return new UsernamePasswordAuthenticationToken(securityPrincipal, "n/a", Set.of());
    }

    private int count(String table, String where) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + where, Integer.class);
    }

    /** Captura o token bruto do convite para exercitar o aceite fim-a-fim. */
    static final class CapturingNotificationGateway implements NotificationGateway {
        volatile String lastRawToken;

        @Override
        public void sendInvitation(EmailAddress email, String rawToken, Instant expiresAt) {
            this.lastRawToken = rawToken;
        }
    }

    @TestConfiguration
    static class TestGatewayConfig {
        @Bean
        @Primary
        CapturingNotificationGateway capturingNotificationGateway() {
            return new CapturingNotificationGateway();
        }
    }
}
