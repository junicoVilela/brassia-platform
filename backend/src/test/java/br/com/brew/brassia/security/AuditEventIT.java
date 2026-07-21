package br.com.brew.brassia.security;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import br.com.brew.brassia.shared.security.SecurityPrincipal;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** SEC-007: auditoria persistida e consultável, escopada à cervejaria ativa. */
@SpringBootTest
@Testcontainers
class AuditEventIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired WebApplicationContext context;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void auditedActionIsPersistedAndQueryableTenantScoped() throws Exception {
        var admin = login("admin@brassia.local", "admin-local-123");

        // Ação administrativa na cervejaria ativa → auditada com brewery_id da MATRIZ.
        mockMvc.perform(post("/api/v1/security/users").session(admin).with(csrf())
                        .contentType("application/json")
                        .content("{\"email\":\"audited@example.com\",\"displayName\":\"Audited\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/security/audit-events").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].action", hasItem("security.user.invite")))
                // Evento global (login, sem cervejaria) não aparece na visão tenant.
                .andExpect(jsonPath("$[*].action", not(hasItem("security.login.success"))));
    }

    @Test
    void auditRequiresPermission() throws Exception {
        var principal = new SecurityPrincipal(UUID.randomUUID(), UUID.randomUUID(), "NoPerm", Set.of());
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, "n/a", Set.of());

        mockMvc.perform(get("/api/v1/security/audit-events").with(authentication(auth)))
                .andExpect(status().isForbidden());
    }

    private MockHttpSession login(String email, String password) throws Exception {
        var result = mockMvc.perform(post("/api/v1/security/login").with(csrf()).contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
