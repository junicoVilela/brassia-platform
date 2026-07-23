package br.com.brew.brassia.recipe;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import br.com.brew.brassia.shared.security.SecurityPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class RecipeImportExportIT {

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
    void exportsAndImportsReportingUnknownFields() throws Exception {
        var session = login();
        var equipmentId = createEquipment(session, "bh-ie");
        var recipeId = createRecipe(session, equipmentId, "Export Me");

        // Exportar em BeerJSON e BeerXML.
        mockMvc.perform(get("/api/v1/recipes/" + recipeId + "/export").param("format", "beerjson").session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.name", is("Export Me")))
                .andExpect(jsonPath("$.equipmentId", is(equipmentId)));
        mockMvc.perform(get("/api/v1/recipes/" + recipeId + "/export").param("format", "beerxml").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<name>Export Me</name>")));

        // Importar BeerJSON válido com um campo desconhecido → cria e reporta o campo.
        var doc = """
                {"name":"Imported Ale","equipmentId":"%s","batchVolumeLiters":400,"boilTimeMinutes":60,
                 "colorMethod":"morey",
                 "items":[{"ingredientId":"%s","stage":"MASH","quantity":20,"unit":"KG"},
                          {"ingredientId":"%s","stage":"BOIL","quantity":30,"unit":"G","timingMinutes":60}]}
                """.formatted(equipmentId, UUID.randomUUID(), UUID.randomUUID());
        mockMvc.perform(post("/api/v1/recipes/import").param("format", "beerjson").session(session).with(csrf())
                        .contentType("application/json").content(doc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("DRAFT")))
                .andExpect(jsonPath("$.unknownFields", hasItem("colorMethod")));
    }

    @Test
    void invalidImportDoesNotPersistAndDeniesWithoutPermission() throws Exception {
        var session = login();

        // Documento inválido (sem equipamento) → 400, nada persiste.
        var invalid = """
                {"name":"No Equipment","batchVolumeLiters":400,
                 "items":[{"ingredientId":"%s","stage":"MASH","quantity":20,"unit":"KG"}]}
                """.formatted(UUID.randomUUID());
        mockMvc.perform(post("/api/v1/recipes/import").param("format", "beerjson").session(session).with(csrf())
                        .contentType("application/json").content(invalid))
                .andExpect(status().isBadRequest());

        // Importar sem permissão → 403.
        mockMvc.perform(post("/api/v1/recipes/import").param("format", "beerjson")
                        .with(authentication(principal(Set.of("recipe.read")))).with(csrf())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
    }

    private String createEquipment(MockHttpSession session, String code) throws Exception {
        return idOf(mockMvc.perform(post("/api/v1/equipment").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"" + code + "\",\"name\":\"BH\",\"capacityLiters\":500,"
                                + "\"deadSpaceLiters\":20,\"mashEfficiencyPercent\":72,\"boilOffLitersPerHour\":8}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private String createRecipe(MockHttpSession session, String equipmentId, String name) throws Exception {
        var content = """
                {"name":"%s","equipmentId":"%s","batchVolumeLiters":400,"boilTimeMinutes":60,
                 "items":[{"ingredientId":"%s","stage":"MASH","quantity":20,"unit":"KG"},
                          {"ingredientId":"%s","stage":"BOIL","quantity":30,"unit":"G","timingMinutes":60}]}
                """.formatted(name, equipmentId, UUID.randomUUID(), UUID.randomUUID());
        return idOf(mockMvc.perform(post("/api/v1/recipes").session(session).with(csrf())
                        .contentType("application/json").content(content))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private static String idOf(String json) throws Exception {
        return JSON.readTree(json).get("id").asText();
    }

    private MockHttpSession login() throws Exception {
        var result = mockMvc.perform(post("/api/v1/security/login").with(csrf())
                        .contentType("application/json")
                        .content("{\"email\":\"admin@brassia.local\",\"password\":\"admin-local-123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private Authentication principal(Set<String> permissions) {
        var p = new SecurityPrincipal(UUID.randomUUID(), UUID.randomUUID(), "Tester", permissions);
        return new UsernamePasswordAuthenticationToken(p, "n/a", Set.of());
    }
}
