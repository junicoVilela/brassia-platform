package br.com.brew.brassia.referencedata;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class StyleSetIT {

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
    void createsPublishesAndComparesStyle() throws Exception {
        var session = login();
        var sourceId = registerSource(session, "BJCP fonte", "GRANTED");
        var setId = createStyleSet(session, sourceId, "2021");

        // Perfil detalhado preservado (permissão integral).
        mockMvc.perform(get("/api/v1/reference/style-sets/" + setId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.styles[0].hasDetailedProfile").value(true))
                .andExpect(jsonPath("$.styles[0].generalImpression").value("IPA lupulada"));

        mockMvc.perform(post("/api/v1/reference/style-sets/" + setId + "/publish").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        // Comparação: OG dentro (aviso ausente), IBU fora (aviso). Nunca bloqueia.
        mockMvc.perform(post("/api/v1/reference/style-sets/" + setId + "/styles/21A/compare")
                        .session(session).with(csrf()).contentType("application/json")
                        .content("{\"og\":1.060,\"ibu\":90}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checks[?(@.metric=='OG')].withinRange").value(hasItem(true)))
                .andExpect(jsonPath("$.checks[?(@.metric=='IBU')].withinRange").value(hasItem(false)));
    }

    @Test
    void limitedPermissionStripsDetailedProfile() throws Exception {
        var session = login();
        var sourceId = registerSource(session, "BJCP limitada", "LIMITED_PERMISSION");
        var setId = createStyleSet(session, sourceId, "2015");

        mockMvc.perform(get("/api/v1/reference/style-sets/" + setId).session(session))
                .andExpect(status().isOk())
                // Gate: sem GRANTED o perfil detalhado não é guardado; impressão geral permanece.
                .andExpect(jsonPath("$.styles[0].hasDetailedProfile").value(false))
                .andExpect(jsonPath("$.styles[0].generalImpression").value("IPA lupulada"));
    }

    @Test
    void publishBlockedWhenSourcePermissionPending() throws Exception {
        var session = login();
        var sourceId = registerSource(session, "BJCP pendente", "PENDING");
        var setId = createStyleSet(session, sourceId, "2025");

        mockMvc.perform(post("/api/v1/reference/style-sets/" + setId + "/publish").session(session).with(csrf()))
                .andExpect(status().isConflict());
    }

    private String createStyleSet(MockHttpSession session, String sourceId, String edition) throws Exception {
        var body = mockMvc.perform(post("/api/v1/reference/style-sets").session(session).with(csrf())
                        .contentType("application/json").content("""
                                {"sourceId":"%s","authority":"BJCP_BEER","edition":"%s","language":"en",
                                 "effectiveFrom":"2026-07-24T00:00:00Z","attribution":"BJCP.org",
                                 "styles":[{"code":"21A","name":"American IPA","family":"IPA",
                                   "og":{"min":1.056,"max":1.070,"unit":"SG"},
                                   "ibu":{"min":40,"max":70,"unit":"IBU"},
                                   "generalImpression":"IPA lupulada","detailedProfile":"Aroma intenso de lúpulo"}]}
                                """.formatted(sourceId, edition)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("id").asText();
    }

    private String registerSource(MockHttpSession session, String name, String permission) throws Exception {
        var body = mockMvc.perform(post("/api/v1/reference/sources").session(session).with(csrf())
                        .contentType("application/json").content("""
                                {"type":"OFFICIAL_STANDARD","name":"%s","owner":"BJCP","url":"https://bjcp.org",
                                 "licenseName":"BJCP","permissionStatus":"%s","attribution":"BJCP.org"}
                                """.formatted(name, permission)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("id").asText();
    }

    private MockHttpSession login() throws Exception {
        var result = mockMvc.perform(post("/api/v1/security/login").with(csrf())
                        .contentType("application/json")
                        .content("{\"email\":\"admin@brassia.local\",\"password\":\"admin-local-123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
