package br.com.brew.brassia.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class ServiceAccountIT {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired WebApplicationContext context;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void issueAndAuthenticateWithApiKey() throws Exception {
        var admin = login();
        var created = mockMvc.perform(post("/api/v1/security/service-accounts").session(admin).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"scim-bot\",\"name\":\"SCIM Bot\"}"))
                .andExpect(status().isCreated()).andReturn();
        var id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        var issued = mockMvc.perform(post("/api/v1/security/service-accounts/" + id + "/credentials")
                        .session(admin).with(csrf()).contentType("application/json")
                        .content("{\"scopes\":[\"scim.users.read\"]}"))
                .andExpect(status().isOk()).andReturn();
        var rawKey = JsonPath.read(issued.getResponse().getContentAsString(), "$.rawKey");

        mockMvc.perform(get("/api/v1/security/service-accounts/me")
                        .header("Authorization", "Bearer " + rawKey))
                .andExpect(status().isOk());
    }

    private MockHttpSession login() throws Exception {
        var result = mockMvc.perform(post("/api/v1/security/login").with(csrf()).contentType("application/json")
                        .content("{\"email\":\"admin@brassia.local\",\"password\":\"admin-local-123\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
