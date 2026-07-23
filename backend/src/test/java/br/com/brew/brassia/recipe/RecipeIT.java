package br.com.brew.brassia.recipe;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class RecipeIT {

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

    private String recipeBody(String name, String equipmentId, int batch, String mashPcts) {
        return """
                {"name":"%s","equipmentId":"%s","batchVolumeLiters":%d,"boilTimeMinutes":60,
                 "targetIbu":30,"items":[
                   {"ingredientId":"%s","stage":"MASH","quantity":5,"unit":"KG","percentage":%s},
                   {"ingredientId":"%s","stage":"BOIL","quantity":30,"unit":"G","timingMinutes":60}]}
                """.formatted(name, equipmentId, batch, UUID.randomUUID(), mashPcts, UUID.randomUUID());
    }

    @Test
    void createsRecipeListsAndReadsDetail() throws Exception {
        var session = login();
        var equipmentId = createEquipment(session, "bh-r1");

        var created = mockMvc.perform(post("/api/v1/recipes").session(session).with(csrf())
                        .contentType("application/json").content(recipeBody("Hoppy Lager", equipmentId, 400, "100")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("DRAFT")))
                .andReturn().getResponse().getContentAsString();
        var id = JSON.readTree(created).get("id").asText();

        mockMvc.perform(get("/api/v1/recipes").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name", hasItem("Hoppy Lager")));

        mockMvc.perform(get("/api/v1/recipes/" + id).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchVolumeLiters", is(400.0)))
                .andExpect(jsonPath("$.items.length()", is(2)))
                .andExpect(jsonPath("$.items[0].stage", is("MASH")));
    }

    @Test
    void rejectsBatchAboveCapacityBadPercentagesAndUnknownEquipment() throws Exception {
        var session = login();
        var equipmentId = createEquipment(session, "bh-r2"); // capacidade 500

        // Volume acima da capacidade → 400.
        mockMvc.perform(post("/api/v1/recipes").session(session).with(csrf())
                        .contentType("application/json").content(recipeBody("Big Batch", equipmentId, 600, "100")))
                .andExpect(status().isBadRequest());

        // Percentual de mostura que não fecha 100 → 400.
        mockMvc.perform(post("/api/v1/recipes").session(session).with(csrf())
                        .contentType("application/json").content(recipeBody("Bad Pct", equipmentId, 400, "80")))
                .andExpect(status().isBadRequest());

        // Equipamento inexistente → 400.
        mockMvc.perform(post("/api/v1/recipes").session(session).with(csrf())
                        .contentType("application/json")
                        .content(recipeBody("Ghost Eq", UUID.randomUUID().toString(), 400, "100")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsDuplicateNameAndDeniesWithoutPermission() throws Exception {
        var session = login();
        var equipmentId = createEquipment(session, "bh-r3");
        mockMvc.perform(post("/api/v1/recipes").session(session).with(csrf())
                        .contentType("application/json").content(recipeBody("Pale Ale", equipmentId, 400, "100")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/recipes").session(session).with(csrf())
                        .contentType("application/json").content(recipeBody("pale ale", equipmentId, 400, "100")))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/recipes")
                        .with(authentication(principal(UUID.randomUUID(), Set.of("recipe.read")))).with(csrf())
                        .contentType("application/json").content(recipeBody("X", equipmentId, 400, "100")))
                .andExpect(status().isForbidden());
    }

    @Test
    void isolatesRecipesByBrewery() throws Exception {
        var session = login();
        var equipmentId = createEquipment(session, "bh-r4");
        mockMvc.perform(post("/api/v1/recipes").session(session).with(csrf())
                        .contentType("application/json").content(recipeBody("Stout", equipmentId, 400, "100")))
                .andExpect(status().isCreated());

        var other = principal(UUID.randomUUID(), Set.of("recipe.read"));
        mockMvc.perform(get("/api/v1/recipes").with(authentication(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(0)));
    }

    private String createEquipment(MockHttpSession session, String code) throws Exception {
        var body = mockMvc.perform(post("/api/v1/equipment").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"" + code + "\",\"name\":\"BH\",\"capacityLiters\":500,"
                                + "\"deadSpaceLiters\":20,\"mashEfficiencyPercent\":72,\"boilOffLitersPerHour\":8}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("id").asText();
    }

    private MockHttpSession login() throws Exception {
        var result = mockMvc.perform(post("/api/v1/security/login").with(csrf())
                        .contentType("application/json")
                        .content("{\"email\":\"admin@brassia.local\",\"password\":\"admin-local-123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private Authentication principal(UUID breweryId, Set<String> permissions) {
        var p = new SecurityPrincipal(UUID.randomUUID(), breweryId, "Tester", permissions);
        return new UsernamePasswordAuthenticationToken(p, "n/a", Set.of());
    }
}
