package br.com.brew.brassia.fermentation;

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
class YeastHarvestIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String HARVESTS = "/api/v1/fermentation/yeast/harvests";

    @Autowired WebApplicationContext context;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void collectsQuarantinedHarvestAndReleasesItOnApproval() throws Exception {
        var session = login();
        var batchId = startedBatch(session);

        var id = collect(session, batchId, null, 1);
        // Nasce em quarentena: ainda não disponível.
        assertAvailability(session, id, false);

        review(session, id, true, "Viabilidade boa").andExpect(status().isOk());

        assertAvailability(session, id, true);
        mockMvc.perform(get(HARVESTS).param("onlyAvailable", "true").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath(byId(id) + ".status", is(java.util.List.of("APPROVED"))))
                .andExpect(jsonPath(byId(id) + ".available", is(java.util.List.of(true))));
    }

    @Test
    void contaminatedHarvestIsRejectedAndNeverBecomesAvailable() throws Exception {
        var session = login();
        var batchId = startedBatch(session);
        var id = collect(session, batchId, null, 1);

        review(session, id, false, "Contaminação por lactobacillus").andExpect(status().isOk());

        assertAvailability(session, id, false);
        // Revisão é terminal: não dá para aprovar depois.
        review(session, id, true, "mudei de ideia").andExpect(status().isConflict());
    }

    @Test
    void rejectionRequiresReason() throws Exception {
        var session = login();
        var batchId = startedBatch(session);
        var id = collect(session, batchId, null, 1);

        review(session, id, false, null).andExpect(status().isBadRequest());
        // Segue em quarentena, revisável.
        review(session, id, false, "Odor de acetato").andExpect(status().isOk());
    }

    @Test
    void derivesGenerationAndExposesCompleteGenealogy() throws Exception {
        var session = login();
        var batchId = startedBatch(session);

        var g1 = collect(session, batchId, null, 1);
        review(session, g1, true, null).andExpect(status().isOk());
        var g2 = collect(session, batchId, g1, 2);
        review(session, g2, true, null).andExpect(status().isOk());
        var g3 = collect(session, batchId, g2, 3);

        // Genealogia completa: da coleta até a levedura comprada.
        mockMvc.perform(get(HARVESTS + "/" + g3 + "/genealogy").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(3)))
                .andExpect(jsonPath("$[0].generation", is(3)))
                .andExpect(jsonPath("$[1].generation", is(2)))
                .andExpect(jsonPath("$[2].generation", is(1)))
                .andExpect(jsonPath("$[2].parentHarvestId").doesNotExist());
    }

    @Test
    void refusesToPropagateLineageFromUnavailableParent() throws Exception {
        var session = login();
        var batchId = startedBatch(session);

        var quarantined = collect(session, batchId, null, 1);
        // Mãe ainda em quarentena não gera linhagem.
        collectExpecting(session, batchId, quarantined).andExpect(status().isConflict());

        review(session, quarantined, false, "Contaminação").andExpect(status().isOk());
        // Nem mãe reprovada.
        collectExpecting(session, batchId, quarantined).andExpect(status().isConflict());
    }

    @Test
    void rejectsDuplicateCodeAndUnknownOrigin() throws Exception {
        var session = login();
        var batchId = startedBatch(session);
        var code = "LV-" + UUID.randomUUID().toString().substring(0, 6);

        mockMvc.perform(post(HARVESTS).session(session).with(csrf()).contentType("application/json")
                        .content(body(code, batchId, null)))
                .andExpect(status().isCreated());
        mockMvc.perform(post(HARVESTS).session(session).with(csrf()).contentType("application/json")
                        .content(body(code, batchId, null)))
                .andExpect(status().isConflict());
        mockMvc.perform(post(HARVESTS).session(session).with(csrf()).contentType("application/json")
                        .content(body("LV-X" + code, UUID.randomUUID().toString(), null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsViabilityOutOfRange() throws Exception {
        var session = login();
        var batchId = startedBatch(session);

        var content = """
                {"code":"LV-BAD","strainId":"%s","sourceBatchId":"%s","harvestedAt":"2026-07-31T10:00:00Z",
                 "viabilityPercent":120,"condition":"Creme","storageLocation":"Câmara 1","storageTempC":4}
                """.formatted(UUID.randomUUID(), batchId);
        mockMvc.perform(post(HARVESTS).session(session).with(csrf()).contentType("application/json")
                        .content(content))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deniesManageWithoutPermission() throws Exception {
        var session = login();
        var batchId = startedBatch(session);

        mockMvc.perform(post(HARVESTS)
                        .with(authentication(principal(UUID.randomUUID(), Set.of("fermentation.yeast.read"))))
                        .with(csrf()).contentType("application/json")
                        .content(body("LV-DENY", batchId, null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void isolatesByTenant() throws Exception {
        var session = login();
        var batchId = startedBatch(session);
        var id = collect(session, batchId, null, 1);
        review(session, id, true, null).andExpect(status().isOk());

        var other = principal(UUID.randomUUID(),
                Set.of("fermentation.yeast.read", "fermentation.yeast.manage"));
        mockMvc.perform(get(HARVESTS).with(authentication(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(0)));
        mockMvc.perform(get(HARVESTS + "/" + id + "/genealogy").with(authentication(other)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(HARVESTS + "/" + id + "/review").with(authentication(other)).with(csrf())
                        .contentType("application/json").content("{\"approve\":true}"))
                .andExpect(status().isBadRequest());
    }

    // --- helpers ---

    /** Filtro por id: a cervejaria de teste é compartilhada, então contagem total não isola. */
    private static String byId(String id) {
        return "$[?(@.id=='" + id + "')]";
    }

    private void assertAvailability(MockHttpSession session, String id, boolean expected) throws Exception {
        mockMvc.perform(get(HARVESTS).param("onlyAvailable", "true").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath(byId(id) + ".id", is(expected ? java.util.List.of(id) : java.util.List.of())));
    }

    private static String body(String code, String batchId, String parentId) {
        return """
                {"code":"%s","strainId":"%s","sourceBatchId":"%s",%s"harvestedAt":"2026-07-31T10:00:00Z",
                 "viabilityPercent":92.5,"condition":"Creme limpo","storageLocation":"Câmara 1","storageTempC":4}
                """.formatted(code, UUID.randomUUID(), batchId,
                parentId == null ? "" : "\"parentHarvestId\":\"" + parentId + "\",");
    }

    private String collect(MockHttpSession session, String batchId, String parentId, int expectedGeneration)
            throws Exception {
        var response = mockMvc.perform(post(HARVESTS).session(session).with(csrf())
                        .contentType("application/json")
                        .content(body("LV-" + UUID.randomUUID().toString().substring(0, 8), batchId, parentId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.generation", is(expectedGeneration)))
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(response).get("id").asText();
    }

    private org.springframework.test.web.servlet.ResultActions collectExpecting(MockHttpSession session,
            String batchId, String parentId) throws Exception {
        return mockMvc.perform(post(HARVESTS).session(session).with(csrf()).contentType("application/json")
                .content(body("LV-" + UUID.randomUUID().toString().substring(0, 8), batchId, parentId)));
    }

    private org.springframework.test.web.servlet.ResultActions review(MockHttpSession session, String id,
            boolean approve, String note) throws Exception {
        var content = "{\"approve\":" + approve + (note == null ? "" : ",\"note\":\"" + note + "\"") + "}";
        return mockMvc.perform(post(HARVESTS + "/" + id + "/review").session(session).with(csrf())
                .contentType("application/json").content(content));
    }

    private String startedBatch(MockHttpSession session) throws Exception {
        var orderId = releasedOrder(session);
        mockMvc.perform(post("/api/v1/brew-orders/" + orderId + "/start").session(session).with(csrf()))
                .andExpect(status().isOk());
        var listBody = mockMvc.perform(get("/api/v1/production/batches").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (JsonNode node : JSON.readTree(listBody)) {
            if (node.get("orderId").asText().equals(orderId)) {
                return node.get("id").asText();
            }
        }
        throw new AssertionError("lote não encontrado para a ordem " + orderId);
    }

    private String releasedOrder(MockHttpSession session) throws Exception {
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var equipmentId = idOf(mockMvc.perform(post("/api/v1/equipment").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"bh-" + sfx + "\",\"name\":\"BH\",\"capacityLiters\":500,"
                                + "\"deadSpaceLiters\":20,\"mashEfficiencyPercent\":72,\"boilOffLitersPerHour\":8}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        var maltId = createIngredient(session, "MALT", "m-" + sfx, "KG", "{\"potentialSg\":\"1.037\",\"colorEbc\":\"4\"}");
        var hopId = createIngredient(session, "HOP", "h-" + sfx, "G", "{\"alphaAcid\":\"12\"}");
        var yeastId = createIngredient(session, "YEAST", "y-" + sfx, "UNIT", "{\"attenuation\":\"78\"}");
        var content = """
                {"name":"Yeast %s","equipmentId":"%s","batchVolumeLiters":400,"boilTimeMinutes":60,"targetIbu":30,
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
