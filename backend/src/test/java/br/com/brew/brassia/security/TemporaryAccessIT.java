package br.com.brew.brassia.security;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import br.com.brew.brassia.shared.security.SecurityPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** SEC-008: solicitar/listar/revogar acesso temporário; segregação e permissão. */
@SpringBootTest
@Testcontainers
class TemporaryAccessIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired WebApplicationContext context;
    @Autowired JdbcClient jdbc;
    private final ObjectMapper json = new ObjectMapper();
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void requestListAndRevokeTemporaryAccess() throws Exception {
        var admin = login("admin@brassia.local", "admin-local-123");
        var target = targetUser();

        var created = mockMvc.perform(post("/api/v1/security/temporary-access").session(admin).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"userId":"%s","permissionCode":"security.user.read","reason":"suporte","durationHours":4}
                                """.formatted(target)))
                .andExpect(status().isCreated())
                .andReturn();
        var grantId = json.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/v1/security/temporary-access").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(grantId)));

        // Segregação: o próprio solicitante (admin) não pode aprovar a concessão.
        mockMvc.perform(post("/api/v1/security/temporary-access/" + grantId + "/approve").session(admin).with(csrf()))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/security/temporary-access/" + grantId).session(admin).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void requestRequiresPermission() throws Exception {
        var principal = new SecurityPrincipal(UUID.randomUUID(), UUID.randomUUID(), "NoPerm", Set.of());
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, "n/a", Set.of());

        mockMvc.perform(post("/api/v1/security/temporary-access").with(authentication(auth)).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"userId":"%s","permissionCode":"security.user.read","reason":"x","durationHours":1}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    private UUID targetUser() {
        var id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO security_user (id, email, normalized_email, display_name, status)
                VALUES (:id, :email, :email, 'Alvo', 'ACTIVE')
                """)
                .param("id", id).param("email", id + "@x.com").update();
        return id;
    }

    private MockHttpSession login(String email, String password) throws Exception {
        var result = mockMvc.perform(post("/api/v1/security/login").with(csrf()).contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
