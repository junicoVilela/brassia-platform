package br.com.brew.brassia.referencedata;

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
class ImportJobIT {

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
    void submitsValidatesAndPublishesMaterializingDataset() throws Exception {
        var session = login();
        var sourceId = registerSource(session, "BJCP Beer 2021", "GRANTED");

        var jobId = submit(session, sourceId, "application/json", "{\"styles\":[{\"code\":\"21A\"}]}", "REVIEW_REQUIRED");

        mockMvc.perform(post("/api/v1/reference/import-jobs/" + jobId + "/publish").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedDatasetId").isNotEmpty());

        // Dataset materializado e publicado aparece na fonte.
        mockMvc.perform(get("/api/v1/reference/sources/" + sourceId + "/datasets").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PUBLISHED"));
    }

    @Test
    void failsJobOnMalformedJson() throws Exception {
        var session = login();
        var sourceId = registerSource(session, "Fonte JSON", "GRANTED");

        mockMvc.perform(post("/api/v1/reference/sources/" + sourceId + "/import-jobs").session(session).with(csrf())
                        .contentType("application/json").content(submitBody("application/json", "{bad json")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.issues[?(@.code=='schema')]").isNotEmpty());
    }

    @Test
    void failsJobOnUnsupportedMime() throws Exception {
        var session = login();
        var sourceId = registerSource(session, "Fonte MIME", "GRANTED");

        mockMvc.perform(post("/api/v1/reference/sources/" + sourceId + "/import-jobs").session(session).with(csrf())
                        .contentType("application/json").content(submitBody("text/csv", "a,b,c")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.issues[?(@.code=='mime')]").isNotEmpty());
    }

    @Test
    void blocksPublishWhenSourcePermissionPending() throws Exception {
        var session = login();
        var sourceId = registerSource(session, "Fonte pendente", "PENDING");
        var jobId = submit(session, sourceId, "application/json", "{\"ok\":true}", "REVIEW_REQUIRED");

        mockMvc.perform(post("/api/v1/reference/import-jobs/" + jobId + "/publish").session(session).with(csrf()))
                .andExpect(status().isConflict());
    }

    private String submit(MockHttpSession session, String sourceId, String contentType, String payload,
            String expectedStatus) throws Exception {
        var body = mockMvc.perform(post("/api/v1/reference/sources/" + sourceId + "/import-jobs")
                        .session(session).with(csrf()).contentType("application/json")
                        .content(submitBody(contentType, payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("id").asText();
    }

    private static String submitBody(String contentType, String payload) throws Exception {
        return """
                {"datasetVersion":"2021","contentType":"%s","rawPayload":%s}
                """.formatted(contentType, JSON.writeValueAsString(payload));
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
