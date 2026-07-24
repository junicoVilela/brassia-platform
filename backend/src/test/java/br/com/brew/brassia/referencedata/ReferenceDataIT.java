package br.com.brew.brassia.referencedata;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
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
class ReferenceDataIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String PAYLOAD = "{\"styles\":[{\"code\":\"21A\",\"name\":\"American IPA\"}]}";

    @Autowired WebApplicationContext context;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void registersRecordsIdempotentlyAndPublishes() throws Exception {
        var session = login();
        var sourceId = registerSource(session, "BJCP Beer 2021", "GRANTED");

        var firstId = recordDataset(session, sourceId, 201, true);
        // Mesmo conteúdo: idempotente (200, created=false, mesmo id).
        var secondId = recordDataset(session, sourceId, 200, false);
        org.assertj.core.api.Assertions.assertThat(secondId).isEqualTo(firstId);

        mockMvc.perform(post("/api/v1/reference/datasets/" + firstId + "/publish").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/reference/sources").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("BJCP Beer 2021"));

        mockMvc.perform(get("/api/v1/reference/sources/" + sourceId + "/datasets").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PUBLISHED"));
    }

    @Test
    void blocksPublishWhenPermissionPending() throws Exception {
        var session = login();
        var sourceId = registerSource(session, "Fonte pendente", "PENDING");
        var datasetId = recordDataset(session, sourceId, 201, true);

        // Gate de licença: permissão PENDING não autoriza publicação → 409.
        mockMvc.perform(post("/api/v1/reference/datasets/" + datasetId + "/publish").session(session).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void deniesRegisterWithoutManagePermission() throws Exception {
        var readOnly = principal(UUID.randomUUID(), Set.of("reference.read"));
        mockMvc.perform(post("/api/v1/reference/sources").with(authentication(readOnly)).with(csrf())
                        .contentType("application/json").content(sourceBody("X", "GRANTED")))
                .andExpect(status().isForbidden());
    }

    @Test
    void isolatesDatasetsAcrossBreweries() throws Exception {
        var session = login();
        var sourceId = registerSource(session, "Fonte da cervejaria", "GRANTED");
        var datasetId = recordDataset(session, sourceId, 201, true);

        // Outra cervejaria (com permissão) não enxerga a fonte → 400 fora do escopo.
        var otherBrewery = principal(UUID.randomUUID(), Set.of("reference.publish", "reference.read"));
        mockMvc.perform(post("/api/v1/reference/datasets/" + datasetId + "/publish")
                        .with(authentication(otherBrewery)).with(csrf()))
                .andExpect(status().isBadRequest());
    }

    private String registerSource(MockHttpSession session, String name, String permission) throws Exception {
        var body = mockMvc.perform(post("/api/v1/reference/sources").session(session).with(csrf())
                        .contentType("application/json").content(sourceBody(name, permission)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("id").asText();
    }

    private String recordDataset(MockHttpSession session, String sourceId, int expectedStatus, boolean expectedCreated)
            throws Exception {
        var body = mockMvc.perform(post("/api/v1/reference/sources/" + sourceId + "/datasets")
                        .session(session).with(csrf()).contentType("application/json")
                        .content("""
                                {"datasetVersion":"2021","rawPayload":%s,"sourceSystem":"BJCP",
                                 "retrievedAt":"2026-07-24T00:00:00Z","effectiveFrom":"2026-07-24T00:00:00Z"}
                                """.formatted(JSON.writeValueAsString(PAYLOAD))))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.created").value(expectedCreated))
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("id").asText();
    }

    private static String sourceBody(String name, String permission) {
        return """
                {"type":"OFFICIAL_STANDARD","name":"%s","owner":"BJCP","url":"https://bjcp.org",
                 "licenseName":"BJCP","permissionStatus":"%s","attribution":"BJCP.org"}
                """.formatted(name, permission);
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
