package br.com.brew.brassia.water;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

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
class WaterReferenceProfileIT {

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
    void createsPublishesAndListsWithChargeBalance() throws Exception {
        var session = login();
        createProfile(session, "Pilsen", "2026");

        mockMvc.perform(get("/api/v1/water/reference-profiles").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.name=='Pilsen')].chargeBalance.withinTolerance").value(
                        org.hamcrest.Matchers.hasItem(true)));

        // publica pelo id retornado na criação
        var body = createProfile(session, "London", "2026");
        var id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("id").asText();
        mockMvc.perform(post("/api/v1/water/reference-profiles/" + id + "/publish").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void computesChargeBalanceForArbitraryIons() throws Exception {
        var session = login();
        mockMvc.perform(post("/api/v1/water/charge-balance").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"calcium\":50,\"magnesium\":10,\"sodium\":20,\"sulfate\":60,"
                                + "\"chloride\":40,\"bicarbonate\":100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.withinTolerance").value(true));

        mockMvc.perform(post("/api/v1/water/charge-balance").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"calcium\":200,\"magnesium\":0,\"sodium\":0,\"sulfate\":0,"
                                + "\"chloride\":0,\"bicarbonate\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.withinTolerance").value(false));
    }

    @Test
    void blocksDuplicateNameEdition() throws Exception {
        var session = login();
        createProfile(session, "Dublin", "2026");
        mockMvc.perform(post("/api/v1/water/reference-profiles").session(session).with(csrf())
                        .contentType("application/json").content(body("Dublin", "2026")))
                .andExpect(status().isConflict());
    }

    @Test
    void republishBlocked() throws Exception {
        var session = login();
        var body = createProfile(session, "Burton", "2026");
        var id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("id").asText();
        mockMvc.perform(post("/api/v1/water/reference-profiles/" + id + "/publish").session(session).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/water/reference-profiles/" + id + "/publish").session(session).with(csrf()))
                .andExpect(status().isConflict());
    }

    private String createProfile(MockHttpSession session, String name, String edition) throws Exception {
        return mockMvc.perform(post("/api/v1/water/reference-profiles").session(session).with(csrf())
                        .contentType("application/json").content(body(name, edition)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
    }

    private static String body(String name, String edition) {
        return """
                {"name":"%s","region":"Região","edition":"%s","calcium":50,"magnesium":10,"sodium":20,
                 "sulfate":60,"chloride":40,"bicarbonate":100,"alkalinity":15,"hardness":30,"ph":7.2,
                 "sourceName":"Estudo municipal"}
                """.formatted(name, edition);
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
