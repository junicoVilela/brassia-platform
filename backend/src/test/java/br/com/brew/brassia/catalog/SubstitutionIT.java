package br.com.brew.brassia.catalog;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
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
class SubstitutionIT {

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
    void ranksClosestProfileFirstWithComparisons() throws Exception {
        var session = login();
        var target = createHopWithProfile(session, "SUBTGT", "5.5", "7.5");
        createHopWithProfile(session, "SUBNEAR", "5.6", "7.4");
        createHopWithProfile(session, "SUBFAR", "12", "14");

        mockMvc.perform(get(substitutions(target) + "?limit=5").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasProfile").value(true))
                .andExpect(jsonPath("$.matches.length()").value(2))
                .andExpect(jsonPath("$.matches[0].code").value("SUBNEAR"))
                .andExpect(jsonPath("$.matches[0].confidence").value("LOW"))
                .andExpect(jsonPath("$.matches[0].comparisons[0].property").value("alphaAcid"))
                .andExpect(jsonPath("$.matches[0].comparisons[0].similar").value(true))
                .andExpect(jsonPath("$.matches[1].code").value("SUBFAR"));
    }

    @Test
    void reportsMissingProfile() throws Exception {
        var session = login();
        var target = createIngredient(session, "SUBNOPROF");

        mockMvc.perform(get(substitutions(target)).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasProfile").value(false))
                .andExpect(jsonPath("$.matches.length()").value(0));
    }

    @Test
    void notFoundForUnknownIngredient() throws Exception {
        var session = login();
        mockMvc.perform(get(substitutions(UUID.randomUUID().toString())).session(session))
                .andExpect(status().isNotFound());
    }

    private static String substitutions(String ingredientId) {
        return "/api/v1/catalog/ingredients/" + ingredientId + "/substitutions";
    }

    private String createHopWithProfile(MockHttpSession session, String code, String alphaMin, String alphaMax)
            throws Exception {
        var id = createIngredient(session, code);
        var body = """
                {"manufacturer":"Yakima","origin":"US","form":"PELLET","purpose":"BITTERING",
                 "ranges":{"alphaAcid":{"min":%s,"max":%s,"unit":"%%"}},
                 "descriptors":["cítrico"],"sourceName":"Fonte %s"}
                """.formatted(alphaMin, alphaMax, code);
        mockMvc.perform(post("/api/v1/catalog/ingredients/" + id + "/technical-profile").session(session).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated());
        return id;
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
