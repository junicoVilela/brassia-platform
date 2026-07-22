package br.com.brew.brassia.catalog;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class CatalogIngredientIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired WebApplicationContext context;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void createsListsUpdatesAndEnforcesOptimisticLock() throws Exception {
        var session = login();

        var created = mockMvc.perform(post("/api/v1/catalog/ingredients").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"type":"MALT","code":"pilsen","name":"Malte Pilsen","useUnit":"KG",
                                 "purchaseUnit":"KG","attributes":{"colorEbc":"3.5"}}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code", is("PILSEN")))
                .andExpect(jsonPath("$.type", is("MALT")))
                .andExpect(jsonPath("$.version", is(0)))
                .andReturn().getResponse().getContentAsString();
        var id = JSON.readTree(created).get("id").asText();

        mockMvc.perform(get("/api/v1/catalog/ingredients").param("type", "MALT").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].code", hasItem("PILSEN")));

        mockMvc.perform(put("/api/v1/catalog/ingredients/" + id).session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"name":"Malte Pilsen DE","useUnit":"G","purchaseUnit":"KG",
                                 "attributes":{"potentialSg":"1.037"},"version":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.useUnit", is("G")))
                .andExpect(jsonPath("$.version", is(1)));

        // Versão stale → 409.
        mockMvc.perform(put("/api/v1/catalog/ingredients/" + id).session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"name":"X","useUnit":"KG","purchaseUnit":"KG","attributes":{},"version":0}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsInvalidUnitAndDuplicateCode() throws Exception {
        var session = login();

        // Unidade fora do vocabulário → 400.
        mockMvc.perform(post("/api/v1/catalog/ingredients").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"type":"HOP","code":"citra","name":"Citra","useUnit":"TON",
                                 "purchaseUnit":"KG","attributes":{"alphaAcid":"12"}}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/catalog/ingredients").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"type":"HOP","code":"saaz","name":"Saaz","useUnit":"G","purchaseUnit":"KG"}
                                """))
                .andExpect(status().isCreated());

        // Código repetido na mesma cervejaria → 409.
        mockMvc.perform(post("/api/v1/catalog/ingredients").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"type":"MALT","code":"SAAZ","name":"Outro","useUnit":"KG","purchaseUnit":"KG"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void deniesWithoutPermission() throws Exception {
        mockMvc.perform(post("/api/v1/catalog/ingredients")
                        .with(authentication(principal(UUID.randomUUID(), Set.of()))).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"type":"MALT","code":"x","name":"X","useUnit":"KG","purchaseUnit":"KG"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void isolatesIngredientsByBrewery() throws Exception {
        var session = login();
        mockMvc.perform(post("/api/v1/catalog/ingredients").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"type":"YEAST","code":"us-05","name":"US-05","useUnit":"UNIT","purchaseUnit":"UNIT"}
                                """))
                .andExpect(status().isCreated());

        // Outra cervejaria (principal com brewery distinta) não enxerga o ingrediente.
        var other = principal(UUID.randomUUID(), Set.of("catalog.ingredient.read"));
        mockMvc.perform(get("/api/v1/catalog/ingredients").with(authentication(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(0)));
    }

    private MockHttpSession login() throws Exception {
        var result = mockMvc.perform(post("/api/v1/security/login").with(csrf())
                        .contentType("application/json")
                        .content("{\"email\":\"admin@brassia.local\",\"password\":\"admin-local-123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private Authentication principal(UUID breweryId, Set<String> permissions) {
        var p = new SecurityPrincipal(UUID.randomUUID(), breweryId, "Tester", permissions);
        return new UsernamePasswordAuthenticationToken(p, "n/a", Set.of());
    }
}
