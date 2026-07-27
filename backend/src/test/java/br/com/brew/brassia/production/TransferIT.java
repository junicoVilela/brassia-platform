package br.com.brew.brassia.production;

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
class TransferIT {

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
    void transfersToFermenterAndMovesBatchToFermenting() throws Exception {
        var session = login();
        var batchId = startedBatch(session).get("id").asText();
        var fermenter = createEquipment(session, 450); // capacidade 450 L

        mockMvc.perform(post("/api/v1/production/batches/" + batchId + "/transfer").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"destinationEquipmentId\":\"" + fermenter + "\",\"volumeLiters\":390,"
                                + "\"ogSg\":1.052,\"lossesLiters\":8}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.volumeLiters", is(390)));

        // O lote passou a FERMENTING; a transferência é consultável.
        mockMvc.perform(get("/api/v1/production/batches/" + batchId).session(session))
                .andExpect(jsonPath("$.status", is("FERMENTING")));
        mockMvc.perform(get("/api/v1/production/batches/" + batchId + "/transfer").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.destinationEquipmentId", is(fermenter)));

        // Transferência única: repetir → 409.
        mockMvc.perform(post("/api/v1/production/batches/" + batchId + "/transfer").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"destinationEquipmentId\":\"" + fermenter + "\",\"volumeLiters\":390,"
                                + "\"ogSg\":1.052,\"lossesLiters\":8}"))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsVolumeExceedingFermenterCapacity() throws Exception {
        var session = login();
        var batchId = startedBatch(session).get("id").asText(); // lote 400 L
        var fermenter = createEquipment(session, 200); // capacidade 200 L

        mockMvc.perform(post("/api/v1/production/batches/" + batchId + "/transfer").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"destinationEquipmentId\":\"" + fermenter + "\",\"volumeLiters\":390,"
                                + "\"ogSg\":1.052,\"lossesLiters\":5}"))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsMassBalanceExceedingBatchVolume() throws Exception {
        var session = login();
        var batchId = startedBatch(session).get("id").asText(); // lote 400 L
        var fermenter = createEquipment(session, 500);

        // 395 + 20 = 415 > 400 → 409.
        mockMvc.perform(post("/api/v1/production/batches/" + batchId + "/transfer").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"destinationEquipmentId\":\"" + fermenter + "\",\"volumeLiters\":395,"
                                + "\"ogSg\":1.052,\"lossesLiters\":20}"))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsUnknownFermenter() throws Exception {
        var session = login();
        var batchId = startedBatch(session).get("id").asText();

        mockMvc.perform(post("/api/v1/production/batches/" + batchId + "/transfer").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"destinationEquipmentId\":\"" + UUID.randomUUID() + "\",\"volumeLiters\":390,"
                                + "\"ogSg\":1.052,\"lossesLiters\":5}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deniesTransferWithoutPermission() throws Exception {
        var session = login();
        var batchId = startedBatch(session).get("id").asText();
        var fermenter = createEquipment(session, 450);

        mockMvc.perform(post("/api/v1/production/batches/" + batchId + "/transfer")
                        .with(authentication(principal(UUID.randomUUID(), Set.of("production.batch.read")))).with(csrf())
                        .contentType("application/json")
                        .content("{\"destinationEquipmentId\":\"" + fermenter + "\",\"volumeLiters\":390,"
                                + "\"ogSg\":1.052,\"lossesLiters\":5}"))
                .andExpect(status().isForbidden());
    }

    // --- helpers ---

    private String createEquipment(MockHttpSession session, int capacity) throws Exception {
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        return idOf(mockMvc.perform(post("/api/v1/equipment").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"fv-" + sfx + "\",\"name\":\"FV\",\"capacityLiters\":" + capacity + ","
                                + "\"deadSpaceLiters\":5,\"mashEfficiencyPercent\":72,\"boilOffLitersPerHour\":8}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode startedBatch(MockHttpSession session) throws Exception {
        var orderId = releasedOrder(session);
        mockMvc.perform(post("/api/v1/brew-orders/" + orderId + "/start").session(session).with(csrf()))
                .andExpect(status().isOk());
        var listBody = mockMvc.perform(get("/api/v1/production/batches").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String batchId = null;
        for (var node : JSON.readTree(listBody)) {
            if (node.get("orderId").asText().equals(orderId)) {
                batchId = node.get("id").asText();
            }
        }
        var detail = mockMvc.perform(get("/api/v1/production/batches/" + batchId).session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(detail);
    }

    private String releasedOrder(MockHttpSession session) throws Exception {
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var equipmentId = createEquipment(session, 500);
        var maltId = createIngredient(session, "MALT", "m-" + sfx, "KG", "{\"potentialSg\":\"1.037\",\"colorEbc\":\"4\"}");
        var hopId = createIngredient(session, "HOP", "h-" + sfx, "G", "{\"alphaAcid\":\"12\"}");
        var yeastId = createIngredient(session, "YEAST", "y-" + sfx, "UNIT", "{\"attenuation\":\"78\"}");
        var content = """
                {"name":"Tr %s","equipmentId":"%s","batchVolumeLiters":400,"boilTimeMinutes":60,"targetIbu":30,
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
                        .content("{\"recipeId\":\"" + recipeId + "\",\"volumeLiters\":400}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/v1/brew-orders/" + orderId + "/release").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"assignedUserId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk());
        return orderId;
    }

    private String createIngredient(MockHttpSession session, String type, String code, String unit, String attributes)
            throws Exception {
        return idOf(mockMvc.perform(post("/api/v1/catalog/ingredients").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"type\":\"" + type + "\",\"code\":\"" + code + "\",\"name\":\"" + code
                                + "\",\"useUnit\":\"" + unit + "\",\"purchaseUnit\":\"" + unit + "\",\"attributes\":"
                                + attributes + "}"))
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
