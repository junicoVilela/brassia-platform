package br.com.brew.brassia.purchasing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import br.com.brew.brassia.shared.security.SecurityPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
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
class PurchaseNeedIT {

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
    void suggestsShortfallDemandMinusOnHand() throws Exception {
        var session = login();
        var sfx = shortId();
        // Receita (malte 20 KG na batelada de 400 L) publicada, OP de 400 L liberada → demanda 20 KG.
        var maltId = releaseOrderReturningMalt(session, sfx, 400);
        // Estoque: 5 KG recebidos → saldo 5 KG.
        receiveLot(session, maltId, createSupplier(session, sfx), 5);

        var need = needFor(session, maltId);
        assertThat(need).isNotNull();
        assertThat(new BigDecimal(need.get("demand").asText())).isEqualByComparingTo("20");
        assertThat(new BigDecimal(need.get("onHand").asText())).isEqualByComparingTo("5");
        assertThat(new BigDecimal(need.get("suggested").asText())).isEqualByComparingTo("15");
        assertThat(need.get("unit").asText()).isEqualTo("KG");
    }

    @Test
    void omitsFullyCoveredIngredient() throws Exception {
        var session = login();
        var sfx = shortId();
        var maltId = releaseOrderReturningMalt(session, sfx, 400); // demanda 20 KG
        receiveLot(session, maltId, createSupplier(session, sfx), 25); // saldo 25 ≥ demanda

        assertThat(needFor(session, maltId)).isNull();
    }

    @Test
    void deniesWithoutPermission() throws Exception {
        mockMvc.perform(get("/api/v1/purchasing/needs")
                        .with(authentication(principal(UUID.randomUUID(), Set.of("purchasing.supplier.read")))))
                .andExpect(status().isForbidden());
    }

    // --- helpers ---

    /** Necessidade do ingrediente informado, ou null se não houver sugestão. */
    private JsonNode needFor(MockHttpSession session, String ingredientId) throws Exception {
        var body = mockMvc.perform(get("/api/v1/purchasing/needs").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (var node : JSON.readTree(body)) {
            if (node.get("ingredientId").asText().equals(ingredientId)) {
                return node;
            }
        }
        return null;
    }

    private String releaseOrderReturningMalt(MockHttpSession session, String sfx, int volume) throws Exception {
        var equipmentId = idOf(mockMvc.perform(post("/api/v1/equipment").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"bh-" + sfx + "\",\"name\":\"BH\",\"capacityLiters\":500,"
                                + "\"deadSpaceLiters\":20,\"mashEfficiencyPercent\":72,\"boilOffLitersPerHour\":8}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        var maltId = createIngredient(session, "MALT", "m-" + sfx, "{\"potentialSg\":\"1.037\",\"colorEbc\":\"4\"}");
        var hopId = createIngredient(session, "HOP", "h-" + sfx, "{\"alphaAcid\":\"12\"}");
        var yeastId = createIngredient(session, "YEAST", "y-" + sfx, "{\"attenuation\":\"78\"}");
        var content = """
                {"name":"Need %s","equipmentId":"%s","batchVolumeLiters":400,"boilTimeMinutes":60,"targetIbu":30,
                 "items":[{"ingredientId":"%s","stage":"MASH","quantity":20,"unit":"KG"},
                          {"ingredientId":"%s","stage":"BOIL","quantity":60,"unit":"G","timingMinutes":60},
                          {"ingredientId":"%s","stage":"FERMENTATION","quantity":1,"unit":"UNIT"}]}
                """.formatted(sfx, equipmentId, maltId, hopId, yeastId);
        var recipeId = idOf(mockMvc.perform(post("/api/v1/recipes").session(session).with(csrf())
                        .contentType("application/json").content(content))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/v1/recipes/" + recipeId + "/metrics").session(session).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/recipes/" + recipeId + "/publish").session(session).with(csrf()))
                .andExpect(status().isOk());
        var orderId = idOf(mockMvc.perform(post("/api/v1/brew-orders").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"recipeId\":\"" + recipeId + "\",\"volumeLiters\":" + volume + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/v1/brew-orders/" + orderId + "/release").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"assignedUserId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk());
        return maltId;
    }

    private void receiveLot(MockHttpSession session, String ingredientId, String supplierId, int qty)
            throws Exception {
        mockMvc.perform(post("/api/v1/inventory/lots").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"ingredientId\":\"" + ingredientId + "\",\"supplierId\":\"" + supplierId
                                + "\",\"quantity\":" + qty + ",\"unit\":\"KG\",\"unitCost\":4.5,"
                                + "\"expiryDate\":\"2027-10-01\",\"inspection\":\"APPROVED\"}"))
                .andExpect(status().isCreated());
    }

    private String createSupplier(MockHttpSession session, String sfx) throws Exception {
        return idOf(mockMvc.perform(post("/api/v1/suppliers").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"Sup " + sfx + "\",\"code\":\"S-" + sfx + "\"}"))
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

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
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
