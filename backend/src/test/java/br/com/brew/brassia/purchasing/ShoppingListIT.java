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
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class ShoppingListIT {

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
    void groupsBySupplierWithCostWhenPermitted() throws Exception {
        var session = login();
        var sfx = shortId();
        var maltId = releaseOrderReturningMalt(session, sfx, 400, "KG"); // demanda 20 KG
        var supplierId = createSupplier(session, sfx);
        receiveLot(session, maltId, supplierId, 5, "KG", "4.5"); // saldo 5 → sugerido 15

        var groups = fetchList(session);
        var group = groupBySupplier(groups, "Sup " + sfx);
        assertThat(group).isNotNull();
        assertThat(group.get("supplierId").asText()).isEqualTo(supplierId);

        var item = itemByIngredient(group, maltId);
        assertThat(item).isNotNull();
        assertThat(new BigDecimal(item.get("suggested").asText())).isEqualByComparingTo("15");
        assertThat(item.get("unit").asText()).isEqualTo("KG");
        assertThat(new BigDecimal(item.get("purchaseQuantity").asText())).isEqualByComparingTo("15");
        assertThat(item.get("purchaseUnit").asText()).isEqualTo("KG");
        assertThat(new BigDecimal(item.get("unitCost").asText())).isEqualByComparingTo("4.5");
        assertThat(new BigDecimal(item.get("estimatedCost").asText())).isEqualByComparingTo("67.5");
        assertThat(new BigDecimal(group.get("estimatedTotal").asText())).isEqualByComparingTo("67.5");
    }

    @Test
    void convertsSuggestedToPurchaseUnit() throws Exception {
        var session = login();
        var sfx = shortId();
        // purchaseUnit = G, uso técnico em KG: sugerido 15 KG → 15000 G.
        var maltId = releaseOrderReturningMalt(session, sfx, 400, "G");
        var supplierId = createSupplier(session, sfx);
        receiveLot(session, maltId, supplierId, 5, "KG", "4.5");

        var item = itemByIngredient(groupBySupplier(fetchList(session), "Sup " + sfx), maltId);
        assertThat(item.get("unit").asText()).isEqualTo("KG");
        assertThat(item.get("purchaseUnit").asText()).isEqualTo("G");
        assertThat(new BigDecimal(item.get("purchaseQuantity").asText())).isEqualByComparingTo("15000");
    }

    @Test
    void roundsPurchaseUpToClosedPackages() throws Exception {
        var session = login();
        var sfx = shortId();
        // Embalagem de 25 KG; demanda 20, sem estoque → sugerido 20 → 1 pacote (25 KG).
        var maltId = releaseOrderReturningMalt(session, sfx, 400, "KG", "25");
        var supplierId = createSupplier(session, sfx);
        receiveLot(session, maltId, supplierId, 0 + 1, "KG", "4.5"); // 1 KG em estoque → sugerido 19

        var item = itemByIngredient(groupBySupplier(fetchList(session), "Sup " + sfx), maltId);
        assertThat(new BigDecimal(item.get("suggested").asText())).isEqualByComparingTo("19");
        assertThat(item.get("packages").asInt()).isEqualTo(1);
        assertThat(new BigDecimal(item.get("purchaseQuantity").asText())).isEqualByComparingTo("25");
        // Custo reflete o pacote fechado comprado (25 × 4.5), não a necessidade (19).
        assertThat(new BigDecimal(item.get("estimatedCost").asText())).isEqualByComparingTo("112.5");
    }

    @Test
    void omitsCostsWithoutCostPermission() throws Exception {
        var session = login();
        var sfx = shortId();
        var maltId = releaseOrderReturningMalt(session, sfx, 400, "KG");
        var supplierId = createSupplier(session, sfx);
        receiveLot(session, maltId, supplierId, 5, "KG", "4.5");

        // Mesmo tenant, mas sem purchasing.cost.read → custos omitidos.
        var breweryId = breweryOf(session);
        var body = mockMvc.perform(get("/api/v1/purchasing/shopping-list")
                        .with(authentication(principal(breweryId, Set.of("purchasing.purchase.read")))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var groups = JSON.readTree(body);

        var group = groupBySupplier(groups, "Sup " + sfx);
        assertThat(group).isNotNull();
        assertThat(group.get("estimatedTotal").isNull()).isTrue();
        var item = itemByIngredient(group, maltId);
        assertThat(item.get("unitCost").isNull()).isTrue();
        assertThat(item.get("estimatedCost").isNull()).isTrue();
        // A quantidade a comprar continua visível sem a permissão de custo.
        assertThat(new BigDecimal(item.get("suggested").asText())).isEqualByComparingTo("15");
    }

    @Test
    void deniesWithoutPurchaseReadPermission() throws Exception {
        mockMvc.perform(get("/api/v1/purchasing/shopping-list")
                        .with(authentication(principal(UUID.randomUUID(), Set.of("purchasing.supplier.read")))))
                .andExpect(status().isForbidden());
    }

    // --- helpers ---

    private JsonNode fetchList(MockHttpSession session) throws Exception {
        var body = mockMvc.perform(get("/api/v1/purchasing/shopping-list").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(body);
    }

    private static JsonNode groupBySupplier(JsonNode groups, String supplierName) {
        for (var group : groups) {
            if (group.get("supplierName").asText().equals(supplierName)) {
                return group;
            }
        }
        return null;
    }

    private static JsonNode itemByIngredient(JsonNode group, String ingredientId) {
        for (var item : group.get("items")) {
            if (item.get("ingredientId").asText().equals(ingredientId)) {
                return item;
            }
        }
        return null;
    }

    private UUID breweryOf(MockHttpSession session) {
        var ctx = (SecurityContext) session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        return ((SecurityPrincipal) ctx.getAuthentication().getPrincipal()).breweryId();
    }

    private String releaseOrderReturningMalt(MockHttpSession session, String sfx, int volume, String purchaseUnit)
            throws Exception {
        return releaseOrderReturningMalt(session, sfx, volume, purchaseUnit, null);
    }

    private String releaseOrderReturningMalt(MockHttpSession session, String sfx, int volume, String purchaseUnit,
            String packageSize) throws Exception {
        var equipmentId = idOf(mockMvc.perform(post("/api/v1/equipment").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"bh-" + sfx + "\",\"name\":\"BH\",\"capacityLiters\":500,"
                                + "\"deadSpaceLiters\":20,\"mashEfficiencyPercent\":72,\"boilOffLitersPerHour\":8}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        var packageField = packageSize == null ? "" : ",\"purchasePackageSize\":" + packageSize;
        var maltId = idOf(mockMvc.perform(post("/api/v1/catalog/ingredients").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"type\":\"MALT\",\"code\":\"m-" + sfx + "\",\"name\":\"m-" + sfx
                                + "\",\"useUnit\":\"KG\",\"purchaseUnit\":\"" + purchaseUnit + "\"" + packageField
                                + ",\"attributes\":{\"potentialSg\":\"1.037\",\"colorEbc\":\"4\"}}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        var hopId = createIngredient(session, "HOP", "h-" + sfx, "{\"alphaAcid\":\"12\"}");
        var yeastId = createIngredient(session, "YEAST", "y-" + sfx, "{\"attenuation\":\"78\"}");
        var content = """
                {"name":"Shop %s","equipmentId":"%s","batchVolumeLiters":400,"boilTimeMinutes":60,"targetIbu":30,
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

    private void receiveLot(MockHttpSession session, String ingredientId, String supplierId, int qty, String unit,
            String unitCost) throws Exception {
        mockMvc.perform(post("/api/v1/inventory/lots").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"ingredientId\":\"" + ingredientId + "\",\"supplierId\":\"" + supplierId
                                + "\",\"quantity\":" + qty + ",\"unit\":\"" + unit + "\",\"unitCost\":" + unitCost
                                + ",\"expiryDate\":\"2027-10-01\",\"inspection\":\"APPROVED\"}"))
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
