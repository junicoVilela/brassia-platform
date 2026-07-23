package br.com.brew.brassia.recipe;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import br.com.brew.brassia.recipe.RecipeLookup;
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
class RecipePublishIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired WebApplicationContext context;
    @Autowired RecipeLookup recipeLookup;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void publishesFreezesAndCreatesNewVersion() throws Exception {
        var session = login();
        var equipmentId = createEquipment(session, "bh-pa");
        var recipeId = createRecipe(session, equipmentId, "Publish Me");

        // Antes de publicar, o lookup publicado não a devolve.
        var brewery = breweryOf(session, recipeId);

        mockMvc.perform(post("/api/v1/recipes/" + recipeId + "/publish").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PUBLISHED")))
                .andExpect(jsonPath("$.version", is(1)));

        // Consulta publicada agora devolve o snapshot estável.
        var published = recipeLookup.findPublished(UUID.fromString(brewery), UUID.fromString(recipeId));
        org.assertj.core.api.Assertions.assertThat(published).isPresent();
        org.assertj.core.api.Assertions.assertThat(published.get().version()).isEqualTo(1);

        // Publicar de novo → 409 (já congelada).
        mockMvc.perform(post("/api/v1/recipes/" + recipeId + "/publish").session(session).with(csrf()))
                .andExpect(status().isConflict());

        // Alteração gera nova versão (novo rascunho, v2, derivado).
        var versionBody = mockMvc.perform(post("/api/v1/recipes/" + recipeId + "/versions").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("DRAFT")))
                .andExpect(jsonPath("$.version", is(2)))
                .andExpect(jsonPath("$.previousRecipeId", is(recipeId)))
                .andReturn().getResponse().getContentAsString();
        var newId = JSON.readTree(versionBody).get("id").asText();
        org.assertj.core.api.Assertions.assertThat(newId).isNotEqualTo(recipeId);
    }

    @Test
    void newVersionOnlyFromPublishedAndDeniesWithoutPermission() throws Exception {
        var session = login();
        var equipmentId = createEquipment(session, "bh-pb");
        var recipeId = createRecipe(session, equipmentId, "Draft Only");

        // Nova versão de um rascunho (não publicado) → 409.
        mockMvc.perform(post("/api/v1/recipes/" + recipeId + "/versions").session(session).with(csrf()))
                .andExpect(status().isConflict());

        // Publicar sem permissão de escrita → 403.
        mockMvc.perform(post("/api/v1/recipes/" + recipeId + "/publish")
                        .with(authentication(principal(Set.of("recipe.read")))).with(csrf()))
                .andExpect(status().isForbidden());
    }

    private String breweryOf(MockHttpSession session, String recipeId) throws Exception {
        // A cervejaria ativa do admin bootstrap — obtida via sessão de login.
        var body = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/security/session").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("activeBrewery").get("id").asText();
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
