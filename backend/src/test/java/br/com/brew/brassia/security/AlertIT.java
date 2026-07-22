package br.com.brew.brassia.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class AlertIT {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void fiveFailuresThen429() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/security/login").with(csrf()).contentType("application/json")
                            .content("{\"email\":\"throttle@example.com\",\"password\":\"wrong\"}"))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/v1/security/login").with(csrf()).contentType("application/json")
                        .content("{\"email\":\"throttle@example.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void afterPenaltyExpiresLoginWorks() throws Exception {
        jdbc.update("DELETE FROM login_throttle");
        mockMvc.perform(post("/api/v1/security/login").with(csrf()).contentType("application/json")
                        .content("{\"email\":\"admin@brassia.local\",\"password\":\"admin-local-123\"}"))
                .andExpect(status().isOk());
    }
}
