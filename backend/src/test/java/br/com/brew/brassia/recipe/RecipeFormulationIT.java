package br.com.brew.brassia.recipe;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
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
class RecipeFormulationIT {

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
    void assistsComparingTargetsWithRanges() throws Exception {
        var session = login();
        String body = """
                {"targets":{"OG":1.060,"IBU":90},
                 "ranges":{"OG":{"min":1.056,"max":1.070,"unit":"SG"},
                           "IBU":{"min":40,"max":70,"unit":"IBU"}}}
                """;

        mockMvc.perform(post("/api/v1/recipes/formulation/assist").session(session).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.attribute=='OG')].status").value(hasItem("WITHIN")))
                .andExpect(jsonPath("$[?(@.attribute=='IBU')].status").value(hasItem("ABOVE")))
                .andExpect(jsonPath("$[?(@.attribute=='IBU')].suggestion").isNotEmpty());
    }

    @Test
    void customProfileWithoutRangeReportsNoRange() throws Exception {
        var session = login();
        String body = "{\"targets\":{\"ABV\":6.0},\"ranges\":{}}";

        mockMvc.perform(post("/api/v1/recipes/formulation/assist").session(session).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.attribute=='ABV')].status").value(hasItem("NO_RANGE")));
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
