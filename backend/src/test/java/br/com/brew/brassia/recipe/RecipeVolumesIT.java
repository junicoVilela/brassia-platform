package br.com.brew.brassia.recipe;

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
class RecipeVolumesIT {

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
    void computesGoldenVolumeBalanceForRecipe() throws Exception {
        var session = login();
        // Equipamento: dead space 20 L, evaporação 8 L/h, capacidade 500 L.
        var equipmentId = createEquipment(session);
        // Receita: batelada final 400 L, fervura 60 min, 20 kg de grão na mostura.
        var recipeId = createRecipe(session, equipmentId);

        mockMvc.perform(get("/api/v1/recipes/" + recipeId + "/volumes").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.method", is("GRAIN_ABSORPTION_BOILOFF_V1")))
                .andExpect(jsonPath("$.grainMassKg", is(20.00)))
                .andExpect(jsonPath("$.finalVolumeLiters", is(400.00)))
                .andExpect(jsonPath("$.grainAbsorptionLiters", is(20.00)))
                .andExpect(jsonPath("$.evaporationLiters", is(8.00)))
                .andExpect(jsonPath("$.lossesLiters", is(20.00)))
                .andExpect(jsonPath("$.preBoilVolumeLiters", is(408.00)))
                .andExpect(jsonPath("$.totalWaterLiters", is(448.00)));
    }

    @Test
    void deniesWithoutPermissionAndUnknownRecipe() throws Exception {
        // Sem permissão → 403.
        mockMvc.perform(get("/api/v1/recipes/" + UUID.randomUUID() + "/volumes")
                        .with(authentication(principal(UUID.randomUUID(), Set.of()))))
                .andExpect(status().isForbidden());

        // Receita inexistente (com permissão) → 400.
        var session = login();
        mockMvc.perform(get("/api/v1/recipes/" + UUID.randomUUID() + "/volumes").session(session))
                .andExpect(status().isBadRequest());
    }

    private String createEquipment(MockHttpSession session) throws Exception {
        var body = mockMvc.perform(post("/api/v1/equipment").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"bh-vol\",\"name\":\"BH\",\"capacityLiters\":500,"
                                + "\"deadSpaceLiters\":20,\"mashEfficiencyPercent\":72,\"boilOffLitersPerHour\":8}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("id").asText();
    }

    private String createRecipe(MockHttpSession session, String equipmentId) throws Exception {
        var content = """
                {"name":"Vol Test","equipmentId":"%s","batchVolumeLiters":400,"boilTimeMinutes":60,
                 "items":[{"ingredientId":"%s","stage":"MASH","quantity":20,"unit":"KG"},
                          {"ingredientId":"%s","stage":"BOIL","quantity":30,"unit":"G","timingMinutes":60}]}
                """.formatted(equipmentId, UUID.randomUUID(), UUID.randomUUID());
        var body = mockMvc.perform(post("/api/v1/recipes").session(session).with(csrf())
                        .contentType("application/json").content(content))
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
