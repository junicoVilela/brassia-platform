package br.com.brew.brassia.planning;

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
class BrewOrderReserveStockIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired WebApplicationContext context;
    MockMvc mockMvc;

    // Guarda os ids dos ingredientes da última OP criada (malte 20 KG, lúpulo 60 G, levedura 1 UNIT).
    private String maltId;
    private String hopId;
    private String yeastId;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void reservesAllMaterialsAtomicallyForReleasedOrder() throws Exception {
        var session = login();
        var sfx = shortId();
        var orderId = releasedOrder(session, sfx);
        var supplierId = createSupplier(session, sfx);
        // Estoque suficiente para todos os itens.
        var maltLot = receiveLot(session, maltId, supplierId, 30, "KG");
        receiveLot(session, hopId, supplierId, 1, "KG"); // 1 KG cobre 60 G
        receiveLot(session, yeastId, supplierId, 5, "UNIT");

        mockMvc.perform(post("/api/v1/brew-orders/" + orderId + "/reserve-stock").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reserved", is(true)))
                .andExpect(jsonPath("$.reservedItems", is(3)));

        // O malte ficou reservado (20 de 30 → disponível 10).
        mockMvc.perform(get("/api/v1/inventory/lots/" + maltLot + "/balance").session(session))
                .andExpect(jsonPath("$.reserved", is(20.0)))
                .andExpect(jsonPath("$.available", is(10.0)));
    }

    @Test
    void insufficientStockReservesNothingAndListsShortfalls() throws Exception {
        var session = login();
        var sfx = shortId();
        var orderId = releasedOrder(session, sfx);
        var supplierId = createSupplier(session, sfx);
        // Malte suficiente, mas levedura ausente → all-or-nothing.
        var maltLot = receiveLot(session, maltId, supplierId, 30, "KG");
        receiveLot(session, hopId, supplierId, 1, "KG");
        // sem lote de levedura

        mockMvc.perform(post("/api/v1/brew-orders/" + orderId + "/reserve-stock").session(session).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.shortfalls[*].ingredientId", hasItem(yeastId)));

        // Nada reservado: o malte segue totalmente disponível.
        mockMvc.perform(get("/api/v1/inventory/lots/" + maltLot + "/balance").session(session))
                .andExpect(jsonPath("$.reserved", is(0.0)))
                .andExpect(jsonPath("$.available", is(30.0)));
    }

    @Test
    void reReserveIsIdempotentAfterRestock() throws Exception {
        var session = login();
        var sfx = shortId();
        var orderId = releasedOrder(session, sfx);
        var supplierId = createSupplier(session, sfx);
        var maltLot = receiveLot(session, maltId, supplierId, 30, "KG");
        receiveLot(session, hopId, supplierId, 1, "KG");
        receiveLot(session, yeastId, supplierId, 5, "UNIT");

        mockMvc.perform(post("/api/v1/brew-orders/" + orderId + "/reserve-stock").session(session).with(csrf()))
                .andExpect(status().isOk());
        // Reexecutar re-sincroniza (libera e reserva de novo) sem duplicar → reservado continua 20.
        mockMvc.perform(post("/api/v1/brew-orders/" + orderId + "/reserve-stock").session(session).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/inventory/lots/" + maltLot + "/balance").session(session))
                .andExpect(jsonPath("$.reserved", is(20.0)))
                .andExpect(jsonPath("$.available", is(10.0)));
    }

    @Test
    void rejectsWhenOrderNotReleased() throws Exception {
        var session = login();
        var sfx = shortId();
        var orderId = draftOrder(session, sfx); // DRAFT, não liberada

        mockMvc.perform(post("/api/v1/brew-orders/" + orderId + "/reserve-stock").session(session).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void deniesWithoutPermission() throws Exception {
        var session = login();
        var sfx = shortId();
        var orderId = releasedOrder(session, sfx);

        mockMvc.perform(post("/api/v1/brew-orders/" + orderId + "/reserve-stock")
                        .with(authentication(principal(UUID.randomUUID(), Set.of("planning.order.read")))).with(csrf()))
                .andExpect(status().isForbidden());
    }

    // --- helpers ---

    private String releasedOrder(MockHttpSession session, String sfx) throws Exception {
        var orderId = draftOrder(session, sfx);
        mockMvc.perform(post("/api/v1/brew-orders/" + orderId + "/release").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"assignedUserId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk());
        return orderId;
    }

    private String draftOrder(MockHttpSession session, String sfx) throws Exception {
        var equipmentId = idOf(mockMvc.perform(post("/api/v1/equipment").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"bh-" + sfx + "\",\"name\":\"BH\",\"capacityLiters\":500,"
                                + "\"deadSpaceLiters\":20,\"mashEfficiencyPercent\":72,\"boilOffLitersPerHour\":8}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        maltId = createIngredient(session, "MALT", "m-" + sfx, "{\"potentialSg\":\"1.037\",\"colorEbc\":\"4\"}");
        hopId = createIngredient(session, "HOP", "h-" + sfx, "{\"alphaAcid\":\"12\"}");
        yeastId = createIngredient(session, "YEAST", "y-" + sfx, "{\"attenuation\":\"78\"}");
        var content = """
                {"name":"Res %s","equipmentId":"%s","batchVolumeLiters":400,"boilTimeMinutes":60,"targetIbu":30,
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
        return idOf(mockMvc.perform(post("/api/v1/brew-orders").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"recipeId\":\"" + recipeId + "\",\"volumeLiters\":400}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private String receiveLot(MockHttpSession session, String ingredientId, String supplierId, int qty, String unit)
            throws Exception {
        return idOf(mockMvc.perform(post("/api/v1/inventory/lots").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"ingredientId\":\"" + ingredientId + "\",\"supplierId\":\"" + supplierId
                                + "\",\"quantity\":" + qty + ",\"unit\":\"" + unit + "\",\"unitCost\":1.0,"
                                + "\"expiryDate\":\"2027-10-01\",\"inspection\":\"APPROVED\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private String createSupplier(MockHttpSession session, String sfx) throws Exception {
        return idOf(mockMvc.perform(post("/api/v1/suppliers").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"Sup " + sfx + "\",\"code\":\"S-" + sfx + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private String createIngredient(MockHttpSession session, String type, String code, String attributes)
            throws Exception {
        var unit = type.equals("YEAST") ? "UNIT" : type.equals("HOP") ? "G" : "KG";
        return idOf(mockMvc.perform(post("/api/v1/catalog/ingredients").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"type\":\"" + type + "\",\"code\":\"" + code + "\",\"name\":\"" + code
                                + "\",\"useUnit\":\"" + unit + "\",\"purchaseUnit\":\"" + unit + "\",\"attributes\":"
                                + attributes + "}"))
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
