package br.com.brew.brassia.security;

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
import com.jayway.jsonpath.JsonPath;
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

@SpringBootTest
@Testcontainers
class ServiceAccountIT {

    @Container @ServiceConnection
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
    void issueAndAuthenticateWithApiKey() throws Exception {
        var admin = login();
        var created = mockMvc.perform(post("/api/v1/security/service-accounts").session(admin).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"scim-bot\",\"name\":\"SCIM Bot\"}"))
                .andExpect(status().isCreated()).andReturn();
        var id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        var issued = mockMvc.perform(post("/api/v1/security/service-accounts/" + id + "/credentials")
                        .session(admin).with(csrf()).contentType("application/json")
                        .content("{\"scopes\":[\"scim.users.read\"]}"))
                .andExpect(status().isOk()).andReturn();
        var rawKey = JsonPath.read(issued.getResponse().getContentAsString(), "$.rawKey");

        mockMvc.perform(get("/api/v1/security/service-accounts/me")
                        .header("Authorization", "Bearer " + rawKey))
                .andExpect(status().isOk());
    }

    @Test
    void listsCredentialsWithoutSecretAndReflectsRevoke() throws Exception {
        var admin = login();
        var created = mockMvc.perform(post("/api/v1/security/service-accounts").session(admin).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"ci-bot\",\"name\":\"CI Bot\"}"))
                .andExpect(status().isCreated()).andReturn();
        var id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        var issued = mockMvc.perform(post("/api/v1/security/service-accounts/" + id + "/credentials")
                        .session(admin).with(csrf()).contentType("application/json")
                        .content("{\"scopes\":[\"scim.users.read\",\"scim.users.write\"]}"))
                .andExpect(status().isOk()).andReturn();
        String credentialId = JsonPath.read(issued.getResponse().getContentAsString(), "$.credentialId");

        mockMvc.perform(get("/api/v1/security/service-accounts/" + id + "/credentials").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].keyPrefix").value(org.hamcrest.Matchers.startsWith("brassia_")))
                .andExpect(jsonPath("$[0].scopes", org.hamcrest.Matchers.hasItems("scim.users.read", "scim.users.write")))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].rawKey").doesNotExist());

        mockMvc.perform(post("/api/v1/security/service-accounts/credentials/" + credentialId + "/revoke")
                        .session(admin).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/security/service-accounts/" + id + "/credentials").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].active").value(false))
                .andExpect(jsonPath("$[0].revokedAt").exists());
    }

    @Test
    void listingCredentialsRequiresPermission() throws Exception {
        var admin = login();
        var created = mockMvc.perform(post("/api/v1/security/service-accounts").session(admin).with(csrf())
                        .contentType("application/json").content("{\"code\":\"nolist\",\"name\":\"No List\"}"))
                .andExpect(status().isCreated()).andReturn();
        var id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        var member = onboardMember();
        mockMvc.perform(get("/api/v1/security/service-accounts/" + id + "/credentials").session(member))
                .andExpect(status().isForbidden());
    }

    private MockHttpSession onboardMember() throws Exception {
        inviteUser.handle(new InviteUserUseCase.Command(
                UUID.randomUUID(), UUID.randomUUID(), "sacred@example.com", "Sem Acesso"));
        acceptInvitation.handle(new AcceptInvitationUseCase.Command(capturedGateway.lastRawToken, "segredo123"));
        var result = mockMvc.perform(post("/api/v1/security/login").with(csrf()).contentType("application/json")
                        .content("{\"email\":\"sacred@example.com\",\"password\":\"segredo123\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private MockHttpSession login() throws Exception {
        var result = mockMvc.perform(post("/api/v1/security/login").with(csrf()).contentType("application/json")
                        .content("{\"email\":\"admin@brassia.local\",\"password\":\"admin-local-123\"}"))
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
