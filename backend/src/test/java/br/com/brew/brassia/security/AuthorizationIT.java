package br.com.brew.brassia.security;

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

/**
 * Verifica o RBAC ponta a ponta: o admin de bootstrap (criado no startup, perfil
 * local) loga e passa a acessar os endpoints permissionados — as permissões são
 * resolvidas dos grupos e injetadas no principal da sessão.
 */
@SpringBootTest
@Testcontainers
class AuthorizationIT {

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
    void bootstrapAdminLoginUnlocksPermissionedEndpoints() throws Exception {
        var login = mockMvc.perform(post("/api/v1/security/login")
                        .with(csrf()).contentType("application/json")
                        .content("{\"email\":\"admin@brassia.local\",\"password\":\"admin-local-123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions", hasItem("security.user.read")))
                .andReturn();
        var session = (MockHttpSession) login.getRequest().getSession(false);

        // Login já traz a cervejaria default (bootstrap) como ativa.
        login.getResponse().setCharacterEncoding("UTF-8");
        var body = login.getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(com.jayway.jsonpath.JsonPath.<String>read(body, "$.activeBrewery.code"))
                .isEqualTo("MATRIZ");

        // Com a permissão resolvida, o endpoint antes 403 agora responde 200.
        mockMvc.perform(get("/api/v1/security/users").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").exists());

        mockMvc.perform(get("/api/v1/security/session").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions", hasItem("security.user.invite")))
                .andExpect(jsonPath("$.accessibleBreweries[*].code", hasItem("MATRIZ")));
    }

    @Test
    void switchBreweryAcceptsAccessibleAndRejectsUnknown() throws Exception {
        var login = mockMvc.perform(post("/api/v1/security/login")
                        .with(csrf()).contentType("application/json")
                        .content("{\"email\":\"admin@brassia.local\",\"password\":\"admin-local-123\"}"))
                .andExpect(status().isOk()).andReturn();
        var session = (MockHttpSession) login.getRequest().getSession(false);
        login.getResponse().setCharacterEncoding("UTF-8");
        String activeId = com.jayway.jsonpath.JsonPath.read(login.getResponse().getContentAsString(), "$.activeBrewery.id");

        mockMvc.perform(post("/api/v1/security/session/brewery").session(session).with(csrf())
                        .contentType("application/json").content("{\"breweryId\":\"" + activeId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeBrewery.code").value("MATRIZ"));

        // Cervejaria não acessível → 403.
        mockMvc.perform(post("/api/v1/security/session/brewery").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"breweryId\":\"" + java.util.UUID.randomUUID() + "\"}"))
                .andExpect(status().isForbidden());
    }
}
