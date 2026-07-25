package br.com.brew.brassia.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class FederationIT {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void createAndValidateOidcProvider() throws Exception {
        var admin = login();
        var created = mockMvc.perform(post("/api/v1/security/federation-providers").session(admin).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"code":"corp-oidc","displayName":"Corp OIDC","protocol":"OIDC",
                                 "issuerOrEntityId":"https://idp.example.com",
                                 "configuration":{"clientId":"client-1"}}
                                """))
                .andExpect(status().isCreated()).andReturn();
        var id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        mockMvc.perform(post("/api/v1/security/federation-providers/" + id + "/validate").session(admin).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void linksAndListsExternalIdentities() throws Exception {
        var admin = login();
        var created = mockMvc.perform(post("/api/v1/security/federation-providers").session(admin).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"code":"corp-saml","displayName":"Corp SAML","protocol":"SAML",
                                 "issuerOrEntityId":"https://idp.example.com/entity",
                                 "configuration":{}}
                                """))
                .andExpect(status().isCreated()).andReturn();
        String id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        var adminUserId = jdbc.queryForObject(
                "SELECT id FROM security_user WHERE email = 'admin@brassia.local'", String.class);

        // Sem vínculos, a lista vem vazia.
        mockMvc.perform(get("/api/v1/security/federation-providers/" + id + "/identities").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(post("/api/v1/security/federation-providers/" + id + "/identities")
                        .session(admin).with(csrf()).contentType("application/json")
                        .content("{\"userId\":\"" + adminUserId + "\",\"externalSubject\":\"okta|123\","
                                + "\"normalizedEmail\":\"user@corp.example\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/security/federation-providers/" + id + "/identities").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].externalSubject").value("okta|123"))
                .andExpect(jsonPath("$[0].normalizedEmail").value("user@corp.example"))
                .andExpect(jsonPath("$[0].userId").value(adminUserId))
                .andExpect(jsonPath("$[0].linkedAt").exists());
    }

    private MockHttpSession login() throws Exception {
        var result = mockMvc.perform(post("/api/v1/security/login").with(csrf()).contentType("application/json")
                        .content("{\"email\":\"admin@brassia.local\",\"password\":\"admin-local-123\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
