package br.com.brew.brassia.planning;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
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
class BrewOrderIT {

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
    void createsOrderWithUniqueCodeAndFrozenSnapshot() throws Exception {
        var session = login();
        var equipmentId = createEquipment(session, "bh-o1");
        var recipeId = preparedRecipe(session, equipmentId); // métricas calculadas + publicada

        var body = mockMvc.perform(post("/api/v1/brew-orders").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"recipeId\":\"" + recipeId + "\",\"volumeLiters\":400}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("DRAFT")))
                .andExpect(jsonPath("$.code", matchesPattern("OP-\\d{4}-\\d{4}")))
                .andReturn().getResponse().getContentAsString();
        var orderId = JSON.readTree(body).get("id").asText();

        // Snapshot congelado: cálculo da receita + perfil do equipamento.
        mockMvc.perform(get("/api/v1/brew-orders/" + orderId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipe.abv", greaterThan(0.0)))
                .andExpect(jsonPath("$.recipe.ogSg", greaterThan(1.0)))
                .andExpect(jsonPath("$.equipment.capacityLiters", is(500.0)));
    }

    @Test
    void generatesSequentialCodes() throws Exception {
        var session = login();
        var equipmentId = createEquipment(session, "bh-o2");
        var recipeId = preparedRecipe(session, equipmentId);

        var code1 = codeOf(createOrder(session, recipeId, 400));
        var code2 = codeOf(createOrder(session, recipeId, 400));
        org.assertj.core.api.Assertions.assertThat(code1).isNotEqualTo(code2);
        org.assertj.core.api.Assertions.assertThat(code1).matches("OP-\\d{4}-\\d{4}");
    }

    @Test
    void rejectsSnapshotIncompleteWhenMetricsMissing() throws Exception {
        var session = login();
        var equipmentId = createEquipment(session, "bh-o3");
        // Publica SEM calcular métricas → snapshot incompleto.
        var maltId = createIngredient(session, "MALT", "pil-o3", "{\"potentialSg\":\"1.037\"}");
        var recipeId = createRecipe(session, equipmentId, maltId);
        mockMvc.perform(post("/api/v1/recipes/" + recipeId + "/publish").session(session).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/brew-orders").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"recipeId\":\"" + recipeId + "\",\"volumeLiters\":400}"))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsUnpublishedRecipe() throws Exception {
        var session = login();
        var equipmentId = createEquipment(session, "bh-o4");
        var maltId = createIngredient(session, "MALT", "pil-o4", "{\"potentialSg\":\"1.037\"}");
        var recipeId = createRecipe(session, equipmentId, maltId);
        mockMvc.perform(post("/api/v1/recipes/" + recipeId + "/metrics").session(session).with(csrf()))
                .andExpect(status().isOk()); // métricas calculadas, mas não publicada

        mockMvc.perform(post("/api/v1/brew-orders").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"recipeId\":\"" + recipeId + "\",\"volumeLiters\":400}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deniesWithoutPermissionAndAcrossBrewery() throws Exception {
        var session = login();
        var equipmentId = createEquipment(session, "bh-o5");
        var recipeId = preparedRecipe(session, equipmentId);

        mockMvc.perform(post("/api/v1/brew-orders")
                        .with(authentication(principal(UUID.randomUUID(), Set.of("planning.order.read")))).with(csrf())
                        .contentType("application/json")
                        .content("{\"recipeId\":\"" + recipeId + "\",\"volumeLiters\":400}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/brew-orders")
                        .with(authentication(principal(UUID.randomUUID(), Set.of("planning.order.manage")))).with(csrf())
                        .contentType("application/json")
                        .content("{\"recipeId\":\"" + recipeId + "\",\"volumeLiters\":400}"))
                .andExpect(status().isBadRequest());
    }

    // --- helpers ---

    private String createOrder(MockHttpSession session, String recipeId, int volume) throws Exception {
        return mockMvc.perform(post("/api/v1/brew-orders").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"recipeId\":\"" + recipeId + "\",\"volumeLiters\":" + volume + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    private static String codeOf(String json) throws Exception {
        return JSON.readTree(json).get("code").asText();
    }

    /** Receita com métricas calculadas e publicada — snapshot completo. */
    private String preparedRecipe(MockHttpSession session, String equipmentId) throws Exception {
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var maltId = createIngredient(session, "MALT", "m-" + sfx, "{\"potentialSg\":\"1.037\",\"colorEbc\":\"4\"}");
        var hopId = createIngredient(session, "HOP", "h-" + sfx, "{\"alphaAcid\":\"12\"}");
        var yeastId = createIngredient(session, "YEAST", "y-" + sfx, "{\"attenuation\":\"78\"}");
        var recipeId = createRecipe(session, equipmentId, maltId, hopId, yeastId);
        mockMvc.perform(post("/api/v1/recipes/" + recipeId + "/metrics").session(session).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/recipes/" + recipeId + "/publish").session(session).with(csrf()))
                .andExpect(status().isOk());
        return recipeId;
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

    private String createRecipe(MockHttpSession session, String equipmentId, String... ingredientIds)
            throws Exception {
        var malt = ingredientIds[0];
        var hop = ingredientIds.length > 1 ? ingredientIds[1] : malt;
        var yeast = ingredientIds.length > 2 ? ingredientIds[2] : malt;
        var content = """
                {"name":"OP Recipe %s","equipmentId":"%s","batchVolumeLiters":400,"boilTimeMinutes":60,
                 "targetIbu":30,"items":[
                   {"ingredientId":"%s","stage":"MASH","quantity":20,"unit":"KG"},
                   {"ingredientId":"%s","stage":"BOIL","quantity":60,"unit":"G","timingMinutes":60},
                   {"ingredientId":"%s","stage":"FERMENTATION","quantity":1,"unit":"UNIT"}]}
                """.formatted(UUID.randomUUID(), equipmentId, malt, hop, yeast);
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

    private Authentication principal(UUID breweryId, Set<String> permissions) {
        var p = new SecurityPrincipal(UUID.randomUUID(), breweryId, "Tester", permissions);
        return new UsernamePasswordAuthenticationToken(p, "n/a", Set.of());
    }
}
