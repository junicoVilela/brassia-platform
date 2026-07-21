package br.com.brew.brassia.security;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** RBAC administração: associar um usuário ao grupo destrava as telas permissionadas. */
@SpringBootTest
@Testcontainers
class AccessManagementIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired WebApplicationContext context;
    @Autowired InviteUserUseCase inviteUser;
    @Autowired AcceptInvitationUseCase acceptInvitation;
    @Autowired CapturingNotificationGateway capturedGateway;
    @Autowired JdbcTemplate jdbc;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void grantingMembershipUnlocksPermissionedEndpoints() throws Exception {
        var memberId = onboard("member@example.com", "segredo123");
        var memberSession = login("member@example.com", "segredo123");

        // Sem grupo, o usuário não tem permissão.
        mockMvc.perform(get("/api/v1/security/users").session(memberSession)).andExpect(status().isForbidden());

        var adminSession = login("admin@brassia.local", "admin-local-123");
        mockMvc.perform(get("/api/v1/security/permissions").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", hasItem("security.user.read")));
        mockMvc.perform(get("/api/v1/security/groups").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", hasItem("ADMINISTRATORS")));

        var adminGroupId = jdbc.queryForObject(
                "SELECT id FROM security_group WHERE code = 'ADMINISTRATORS'", UUID.class);
        mockMvc.perform(post("/api/v1/security/users/" + memberId + "/memberships")
                        .session(adminSession).with(csrf()).contentType("application/json")
                        .content("{\"groupId\":\"" + adminGroupId + "\"}"))
                .andExpect(status().isNoContent());

        // Relogado, o usuário agora tem a permissão (associação na cervejaria ativa).
        var refreshed = login("member@example.com", "segredo123");
        mockMvc.perform(get("/api/v1/security/users").session(refreshed)).andExpect(status().isOk());
    }

    @Test
    void membershipManagementRequiresPermission() throws Exception {
        onboard("nogroup@example.com", "segredo123");
        var session = login("nogroup@example.com", "segredo123");

        mockMvc.perform(post("/api/v1/security/users/" + UUID.randomUUID() + "/memberships")
                        .session(session).with(csrf()).contentType("application/json")
                        .content("{\"groupId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isForbidden());
    }

    private UUID onboard(String email, String password) throws Exception {
        var invite = inviteUser.handle(new InviteUserUseCase.Command(UUID.randomUUID(), UUID.randomUUID(), email, "Member"));
        acceptInvitation.handle(new AcceptInvitationUseCase.Command(capturedGateway.lastRawToken, password));
        return invite.userId();
    }

    private MockHttpSession login(String email, String password) throws Exception {
        var result = mockMvc.perform(post("/api/v1/security/login").with(csrf())
                        .contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    static final class CapturingNotificationGateway implements NotificationGateway {
        volatile String lastRawToken;

        @Override public void sendInvitation(EmailAddress email, String rawToken, Instant expiresAt) {
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
