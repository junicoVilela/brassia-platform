package br.com.brew.brassia.catalog;

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
class TechnicalProfileIT {

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
    void createsPublishesAndReadsProfile() throws Exception {
        var session = login();
        var ingredientId = createIngredient(session, "CASCADE");

        mockMvc.perform(post(profile(ingredientId)).session(session).with(csrf()).contentType("application/json")
                        .content(PROFILE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"));

        mockMvc.perform(get(profile(ingredientId)).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.ranges.alphaAcid.max").value(7.5))
                .andExpect(jsonPath("$.descriptors[0]").value("cítrico"))
                .andExpect(jsonPath("$.sourceName").value("Yakima Chief"));

        mockMvc.perform(post(profile(ingredientId) + "/publish").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        mockMvc.perform(get(profile(ingredientId)).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void blocksDuplicateProfile() throws Exception {
        var session = login();
        var ingredientId = createIngredient(session, "MAGNUM");
        createProfile(session, ingredientId);

        mockMvc.perform(post(profile(ingredientId)).session(session).with(csrf()).contentType("application/json")
                        .content(PROFILE_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void republishBlocked() throws Exception {
        var session = login();
        var ingredientId = createIngredient(session, "SAAZ");
        createProfile(session, ingredientId);
        mockMvc.perform(post(profile(ingredientId) + "/publish").session(session).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(post(profile(ingredientId) + "/publish").session(session).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void notFoundWhenNoProfile() throws Exception {
        var session = login();
        var ingredientId = createIngredient(session, "CENTENNIAL");
        mockMvc.perform(get(profile(ingredientId)).session(session))
                .andExpect(status().isNotFound());
    }

    private static final String PROFILE_BODY = """
            {"manufacturer":"Yakima","origin":"US","form":"PELLET","purpose":"BITTERING",
             "ranges":{"alphaAcid":{"min":5.5,"max":7.5,"unit":"%"}},
             "descriptors":["cítrico","resinoso"],"sourceName":"Yakima Chief"}
            """;

    private static String profile(String ingredientId) {
        return "/api/v1/catalog/ingredients/" + ingredientId + "/technical-profile";
    }

    private void createProfile(MockHttpSession session, String ingredientId) throws Exception {
        mockMvc.perform(post(profile(ingredientId)).session(session).with(csrf()).contentType("application/json")
                        .content(PROFILE_BODY))
                .andExpect(status().isCreated());
    }

    private String createIngredient(MockHttpSession session, String code) throws Exception {
        var body = mockMvc.perform(post("/api/v1/catalog/ingredients").session(session).with(csrf())
                        .contentType("application/json").content("""
                                {"type":"HOP","code":"%s","name":"Lúpulo %s","useUnit":"G","purchaseUnit":"KG"}
                                """.formatted(code, code)))
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
