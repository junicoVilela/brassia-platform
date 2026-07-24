package br.com.brew.brassia.calculator;

import static org.hamcrest.Matchers.hasItem;
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
class CalculatorIT {

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
    void listsCatalogAndComputesAbv() throws Exception {
        var session = login();

        mockMvc.perform(get("/api/v1/calculators").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(hasItem("abv")));

        mockMvc.perform(post("/api/v1/calculators/abv").session(session).with(csrf())
                        .contentType("application/json").content("{\"inputs\":{\"og\":1.050,\"fg\":1.010}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(5.25))
                .andExpect(jsonPath("$.unit").value("%"))
                .andExpect(jsonPath("$.version").value("1.0"))
                .andExpect(jsonPath("$.method").isNotEmpty());
    }

    @Test
    void rejectsMissingInput() throws Exception {
        var session = login();
        mockMvc.perform(post("/api/v1/calculators/abv").session(session).with(csrf())
                        .contentType("application/json").content("{\"inputs\":{\"og\":1.050}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUnknownCalculator() throws Exception {
        var session = login();
        mockMvc.perform(post("/api/v1/calculators/nope").session(session).with(csrf())
                        .contentType("application/json").content("{\"inputs\":{}}"))
                .andExpect(status().isBadRequest());
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
