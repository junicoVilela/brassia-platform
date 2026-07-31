package br.com.brew.brassia.fermentation;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import br.com.brew.brassia.shared.security.SecurityPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
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
class YeastReuseIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String HARVESTS = "/api/v1/fermentation/yeast/harvests";
    private static final String REUSE = "/api/v1/fermentation/yeast/reuse";
    private static final String POLICY = "/api/v1/fermentation/yeast/policy";

    @Autowired WebApplicationContext context;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void recommendsFreshApprovedHarvestAndExplainsEveryFactor() throws Exception {
        var session = login();
        var batchId = startedBatch(session);
        var strain = UUID.randomUUID().toString();
        var id = approvedHarvest(session, batchId, strain, "92", 3);

        mockMvc.perform(get(REUSE).param("strainId", strain).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations.length()", is(1)))
                .andExpect(jsonPath("$.recommendations[0].recommended", is(true)))
                .andExpect(jsonPath("$.recommendations[0].harvest.id", is(id)))
                .andExpect(jsonPath("$.recommendations[0].blockers.length()", is(0)))
                // Explicável: os três fatores, cada um com sua frase.
                .andExpect(jsonPath("$.recommendations[0].factors.length()", is(3)))
                .andExpect(jsonPath("$.recommendations[0].factors[0].name", is("generation")))
                .andExpect(jsonPath("$.recommendations[0].factors[2].explanation", containsString("Viabilidade")))
                // A política aplicada acompanha o resultado.
                .andExpect(jsonPath("$.policy.maxGeneration", is(10)));
    }

    @Test
    void explainsWhyOldOrWeakYeastIsNotRecommended() throws Exception {
        var session = login();
        var batchId = startedBatch(session);
        var strain = UUID.randomUUID().toString();
        approvedHarvest(session, batchId, strain, "45", 60);

        mockMvc.perform(get(REUSE).param("strainId", strain).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations[0].recommended", is(false)))
                // Idade e viabilidade barram; a geração 1 continua dentro da política.
                .andExpect(jsonPath("$.recommendations[0].blockers.length()", is(2)));
    }

    @Test
    void appliesTheBreweryPolicyToTheRecommendation() throws Exception {
        var session = login();
        var batchId = startedBatch(session);
        var strain = UUID.randomUUID().toString();
        approvedHarvest(session, batchId, strain, "80", 10);

        // Sob a política padrão, é recomendada.
        mockMvc.perform(get(REUSE).param("strainId", strain).session(session))
                .andExpect(jsonPath("$.recommendations[0].recommended", is(true)));

        // Política mais exigente derruba a mesma coleta.
        mockMvc.perform(put(POLICY).session(session).with(csrf()).contentType("application/json")
                        .content("{\"maxGeneration\":5,\"maxAgeDays\":7,\"minViabilityPercent\":85}"))
                .andExpect(status().isOk());

        mockMvc.perform(get(REUSE).param("strainId", strain).session(session))
                .andExpect(jsonPath("$.recommendations[0].recommended", is(false)))
                .andExpect(jsonPath("$.recommendations[0].blockers.length()", is(2)))
                .andExpect(jsonPath("$.policy.maxAgeDays", is(7)));

        // Devolve a política ao padrão para não contaminar os outros testes da classe.
        mockMvc.perform(put(POLICY).session(session).with(csrf()).contentType("application/json")
                        .content("{\"maxGeneration\":10,\"maxAgeDays\":21,\"minViabilityPercent\":70}"))
                .andExpect(status().isOk());
    }

    @Test
    void onlyAvailableHarvestsAreCandidates() throws Exception {
        var session = login();
        var batchId = startedBatch(session);
        var strain = UUID.randomUUID().toString();

        // Em quarentena e reprovada não são candidatas.
        collect(session, batchId, strain, "95", 1);
        var rejected = collect(session, batchId, strain, "95", 1);
        mockMvc.perform(post(HARVESTS + "/" + rejected + "/review").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"approve\":false,\"note\":\"Contaminação\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get(REUSE).param("strainId", strain).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations.length()", is(0)));
    }

    @Test
    void confirmedUseConsumesTheHarvestAndLinksTheBatch() throws Exception {
        var session = login();
        var batchId = startedBatch(session);
        var strain = UUID.randomUUID().toString();
        var id = approvedHarvest(session, batchId, strain, "92", 2);

        mockMvc.perform(post(HARVESTS + "/" + id + "/use").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"targetBatchId\":\"" + batchId + "\",\"confirmed\":true}"))
                .andExpect(status().isOk());

        // Some das recomendações e da lista de disponíveis; o vínculo fica registrado.
        mockMvc.perform(get(REUSE).param("strainId", strain).session(session))
                .andExpect(jsonPath("$.recommendations.length()", is(0)));
        mockMvc.perform(get(HARVESTS).session(session))
                .andExpect(jsonPath("$[?(@.id=='" + id + "')].status", is(java.util.List.of("USED"))))
                .andExpect(jsonPath("$[?(@.id=='" + id + "')].pitchedBatchId", is(java.util.List.of(batchId))));
    }

    @Test
    void useRequiresExplicitConfirmationAndExistingBatch() throws Exception {
        var session = login();
        var batchId = startedBatch(session);
        var id = approvedHarvest(session, batchId, UUID.randomUUID().toString(), "92", 2);

        mockMvc.perform(post(HARVESTS + "/" + id + "/use").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"targetBatchId\":\"" + batchId + "\",\"confirmed\":false}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(HARVESTS + "/" + id + "/use").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"targetBatchId\":\"" + UUID.randomUUID() + "\",\"confirmed\":true}"))
                .andExpect(status().isBadRequest());
        // Continua disponível: nenhuma tentativa recusada consumiu a coleta.
        mockMvc.perform(get(HARVESTS).param("onlyAvailable", "true").session(session))
                .andExpect(jsonPath("$[?(@.id=='" + id + "')].id", is(java.util.List.of(id))));
    }

    @Test
    void theSameHarvestCannotBePitchedTwice() throws Exception {
        var session = login();
        var batchId = startedBatch(session);
        var id = approvedHarvest(session, batchId, UUID.randomUUID().toString(), "92", 2);
        var body = "{\"targetBatchId\":\"" + batchId + "\",\"confirmed\":true}";

        mockMvc.perform(post(HARVESTS + "/" + id + "/use").session(session).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isOk());
        mockMvc.perform(post(HARVESTS + "/" + id + "/use").session(session).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void deniesPolicyChangeWithoutPermission() throws Exception {
        mockMvc.perform(put(POLICY)
                        .with(authentication(principal(UUID.randomUUID(),
                                Set.of("fermentation.yeast.read", "fermentation.yeast.manage"))))
                        .with(csrf()).contentType("application/json")
                        .content("{\"maxGeneration\":5,\"maxAgeDays\":7,\"minViabilityPercent\":85}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void isolatesByTenant() throws Exception {
        var session = login();
        var batchId = startedBatch(session);
        var strain = UUID.randomUUID().toString();
        approvedHarvest(session, batchId, strain, "92", 2);

        var other = principal(UUID.randomUUID(), Set.of("fermentation.yeast.read", "fermentation.yeast.manage"));
        // Outra cervejaria não vê candidatas e recebe a política padrão, não a nossa.
        mockMvc.perform(get(REUSE).param("strainId", strain).with(authentication(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations.length()", is(0)))
                .andExpect(jsonPath("$.policy.maxGeneration", is(10)));
    }

    // --- helpers ---

    private String collect(MockHttpSession session, String batchId, String strainId, String viability, int ageDays)
            throws Exception {
        var harvestedAt = Instant.parse("2026-07-31T10:00:00Z").minus(Duration.ofDays(ageDays));
        var content = """
                {"code":"LV-%s","strainId":"%s","sourceBatchId":"%s","harvestedAt":"%s","viabilityPercent":%s,
                 "condition":"Creme limpo","storageLocation":"Câmara 1","storageTempC":4}
                """.formatted(UUID.randomUUID().toString().substring(0, 8), strainId, batchId, harvestedAt,
                viability);
        var response = mockMvc.perform(post(HARVESTS).session(session).with(csrf())
                        .contentType("application/json").content(content))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(response).get("id").asText();
    }

    private String approvedHarvest(MockHttpSession session, String batchId, String strainId, String viability,
            int ageDays) throws Exception {
        var id = collect(session, batchId, strainId, viability, ageDays);
        mockMvc.perform(post(HARVESTS + "/" + id + "/review").session(session).with(csrf())
                        .contentType("application/json").content("{\"approve\":true}"))
                .andExpect(status().isOk());
        return id;
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
                {"name":"Reuse %s","equipmentId":"%s","batchVolumeLiters":400,"boilTimeMinutes":60,"targetIbu":30,
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
