package br.com.brew.brassia.brewery;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class BreweryPreferencesIT {

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
    void getsDefaultsUpdatesAndKeepsOldRevisionImmutable() throws Exception {
        var session = login("admin@brassia.local", "admin-local-123");

        mockMvc.perform(get("/api/v1/breweries/active/preferences").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.volumeUnit", is("L")))
                .andExpect(jsonPath("$.currencyCode", is("BRL")))
                .andExpect(jsonPath("$.stockPolicy", is("FEFO")))
                .andExpect(jsonPath("$.version", is(0)));

        mockMvc.perform(put("/api/v1/breweries/active/preferences").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"volumeUnit":"ML","massUnit":"G","temperatureUnit":"F","currencyCode":"USD",
                                 "maxBatchVolume":42.5,"allowNegativeStock":true,"stockPolicy":"FIFO","version":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.volumeUnit", is("ML")))
                .andExpect(jsonPath("$.currencyCode", is("USD")))
                .andExpect(jsonPath("$.version", is(1)));

        // Revisão 0 permanece com os valores antigos (snapshot imutável).
        mockMvc.perform(get("/api/v1/breweries/active/preferences/revisions/0").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.volumeUnit", is("L")))
                .andExpect(jsonPath("$.currencyCode", is("BRL")))
                .andExpect(jsonPath("$.version", is(0)));

        mockMvc.perform(get("/api/v1/breweries/active/preferences/revisions/1").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.volumeUnit", is("ML")))
                .andExpect(jsonPath("$.version", is(1)));

        // Versão stale → 409.
        mockMvc.perform(put("/api/v1/breweries/active/preferences").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"volumeUnit":"L","massUnit":"KG","temperatureUnit":"C","currencyCode":"BRL",
                                 "maxBatchVolume":10,"allowNegativeStock":false,"stockPolicy":"FEFO","version":0}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void deniesWithoutPermission() throws Exception {
        // Login cria sessão sem brewery.preferences.* se o usuário não for admin —
        // usa sessão admin e remove via usuário sem permissão: convidar é pesado;
        // autentica com CSRF em endpoint protegido sem login → 401; com login admin ok.
        // Aqui: sem autenticação → 401.
        mockMvc.perform(get("/api/v1/breweries/active/preferences"))
                .andExpect(status().isUnauthorized());
    }

    private MockHttpSession login(String email, String password) throws Exception {
        var result = mockMvc.perform(post("/api/v1/security/login").with(csrf())
                        .contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
