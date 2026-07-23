package br.com.brew.brassia.recipe;

import static org.hamcrest.Matchers.greaterThan;
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
import com.fasterxml.jackson.databind.JsonNode;
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
class RecipeMetricsIT {

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
    void calculatesPersistsAndReportsTolerance() throws Exception {
        var session = login();
        var equipmentId = createEquipment(session, "bh-ma");
        var maltId = createIngredient(session, "MALT", "pilsen", "{\"potentialSg\":\"1.037\",\"colorEbc\":\"4\"}");
        var hopId = createIngredient(session, "HOP", "citra", "{\"alphaAcid\":\"12\"}");
        var yeastId = createIngredient(session, "YEAST", "us05", "{\"attenuation\":\"78\"}");
        var recipeId = createRecipe(session, equipmentId, maltId, hopId, yeastId);

        var calc = post("/api/v1/recipes/" + recipeId + "/metrics");
        var body = mockMvc.perform(calc.session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.method", is("TINSETH_MOREY")))
                .andExpect(jsonPath("$.version", is(1)))
                .andExpect(jsonPath("$.attenuationPercent", is(78.0)))
                .andExpect(jsonPath("$.ibu", greaterThan(0.0)))
                .andExpect(jsonPath("$.ogPoints", greaterThan(0.0)))
                // tolerância explícita vs a meta informada (targetIbu = 30).
                .andExpect(jsonPath("$.ibuCheck.tolerance", is(5.0)))
                .andExpect(jsonPath("$.ibuCheck.target", is(30.0)))
                .andReturn().getResponse().getContentAsString();
        JsonNode calculated = JSON.readTree(body);

        // Persistência: GET devolve as mesmas metas com método/versão.
        mockMvc.perform(get("/api/v1/recipes/" + recipeId + "/metrics").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.method", is("TINSETH_MOREY")))
                .andExpect(jsonPath("$.version", is(1)))
                .andExpect(jsonPath("$.ogPoints", is(calculated.get("ogPoints").asDouble())))
                .andExpect(jsonPath("$.abv", is(calculated.get("abv").asDouble())));
    }

    @Test
    void getBeforeCalculateAndDenyWithoutPermission() throws Exception {
        var session = login();
        var equipmentId = createEquipment(session, "bh-mb");
        var maltId = createIngredient(session, "MALT", "vienna", "{\"potentialSg\":\"1.036\"}");
        var recipeId = createRecipe(session, equipmentId, maltId, maltId, maltId);

        // Metas ainda não calculadas → 400.
        mockMvc.perform(get("/api/v1/recipes/" + recipeId + "/metrics").session(session))
                .andExpect(status().isBadRequest());

        // Calcular sem permissão de escrita → 403.
        mockMvc.perform(post("/api/v1/recipes/" + recipeId + "/metrics")
                        .with(authentication(principal(Set.of("recipe.read")))).with(csrf()))
                .andExpect(status().isForbidden());
    }

    private String createEquipment(MockHttpSession session, String code) throws Exception {
        return idOf(mockMvc.perform(post("/api/v1/equipment").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"" + code + "\",\"name\":\"BH\",\"capacityLiters\":500,"
                                + "\"deadSpaceLiters\":20,\"mashEfficiencyPercent\":72,\"boilOffLitersPerHour\":8}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private String createIngredient(MockHttpSession session, String type, String code, String attributes)
            throws Exception {
        return idOf(mockMvc.perform(post("/api/v1/catalog/ingredients").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"type\":\"" + type + "\",\"code\":\"" + code + "\",\"name\":\"" + code
                                + "\",\"useUnit\":\"KG\",\"purchaseUnit\":\"KG\",\"attributes\":" + attributes + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private String createRecipe(MockHttpSession session, String equipmentId, String maltId, String hopId,
            String yeastId) throws Exception {
        var content = """
                {"name":"Metrics %s","equipmentId":"%s","batchVolumeLiters":400,"boilTimeMinutes":60,
                 "targetIbu":30,"items":[
                   {"ingredientId":"%s","stage":"MASH","quantity":20,"unit":"KG"},
                   {"ingredientId":"%s","stage":"BOIL","quantity":60,"unit":"G","timingMinutes":60},
                   {"ingredientId":"%s","stage":"FERMENTATION","quantity":1,"unit":"UNIT"}]}
                """.formatted(UUID.randomUUID(), equipmentId, maltId, hopId, yeastId);
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
