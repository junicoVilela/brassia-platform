package br.com.brew.brassia.recipe;

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

/** REC-007/REC-008: prévia (dry-run) e segurança da importação (tamanho, XXE). */
@SpringBootTest
@Testcontainers
class RecipeImportSecurityIT {

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
    void previewReportsWithoutPersisting() throws Exception {
        var session = login();
        String beerJson = "{\"version\":1,\"name\":\"Prévia\",\"equipmentId\":\"" + java.util.UUID.randomUUID()
                + "\",\"batchVolumeLiters\":200,\"items\":[{\"ingredientId\":\"" + java.util.UUID.randomUUID()
                + "\",\"stage\":\"MASH\",\"quantity\":10,\"unit\":\"KG\"}],\"unknownX\":1}";

        mockMvc.perform(post("/api/v1/recipes/import/preview?format=beerjson").session(session).with(csrf())
                        .contentType("application/json").content(beerJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importable").value(true))
                .andExpect(jsonPath("$.itemCount").value(1))
                .andExpect(jsonPath("$.unknownFields[0]").value("unknownX"));

        // Prévia não persiste: a listagem continua vazia.
        mockMvc.perform(get("/api/v1/recipes").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void rejectsOversizedImport() throws Exception {
        var session = login();
        String huge = "{\"name\":\"" + "a".repeat(1024 * 1024 + 10) + "\"}";

        mockMvc.perform(post("/api/v1/recipes/import?format=beerjson").session(session).with(csrf())
                        .contentType("application/json").content(huge))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsBeerXmlWithDoctype() throws Exception {
        var session = login();
        // XXE/entity-expansion: DTD é recusado (disallow-doctype-decl).
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE recipe [<!ENTITY x \"y\">]><recipe><name>&x;</name></recipe>";

        mockMvc.perform(post("/api/v1/recipes/import/preview?format=beerxml").session(session).with(csrf())
                        .contentType("application/xml").content(xxe))
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
