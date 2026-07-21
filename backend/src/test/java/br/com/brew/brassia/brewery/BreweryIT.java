package br.com.brew.brassia.brewery;

import static org.hamcrest.Matchers.hasItem;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class BreweryIT {

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
    void registersAndListsThenRejectsDuplicate() throws Exception {
        var manager = principal(Set.of("brewery.manage", "brewery.read"));

        mockMvc.perform(post("/api/v1/breweries").with(authentication(manager)).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"sb40\",\"name\":\"Casa Brew\",\"timezone\":\"America/Sao_Paulo\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SB40"));

        mockMvc.perform(get("/api/v1/breweries").with(authentication(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].code", hasItem("SB40")));

        // Código duplicado (case-insensitive pela normalização) → 409.
        mockMvc.perform(post("/api/v1/breweries").with(authentication(manager)).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"SB40\",\"name\":\"Outra\",\"timezone\":\"UTC\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void deniesRegisterWithoutPermission() throws Exception {
        mockMvc.perform(post("/api/v1/breweries").with(authentication(principal(Set.of()))).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"nope\",\"name\":\"X\",\"timezone\":\"UTC\"}"))
                .andExpect(status().isForbidden());
    }

    private Authentication principal(Set<String> permissions) {
        var p = new SecurityPrincipal(UUID.randomUUID(), UUID.randomUUID(), "Admin", permissions);
        return new UsernamePasswordAuthenticationToken(p, "n/a", Set.of());
    }
}
