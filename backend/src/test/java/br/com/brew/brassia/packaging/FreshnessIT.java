package br.com.brew.brassia.packaging;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class FreshnessIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    /**
     * Janela do envase ancorada em AGORA, e não numa data fixa.
     *
     * <p>A linha limpa exige liberação <strong>anterior</strong> ao início planejado
     * ({@code LineCleanliness}). Com data fixa, o dia em que ela passa inverte a ordem e todo envase
     * destes testes passa a ser recusado com {@code line_not_clean} — uma falha datada, que aparece sem
     * ninguém ter mexido em nada.
     */
    private static final String PLANNED_START = Instant.now().plus(Duration.ofHours(1)).toString();
    private static final String PLANNED_END = Instant.now().plus(Duration.ofHours(7)).toString();

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String PLANS = "/api/v1/packaging/plans";
    private static final String POLICY = "/api/v1/packaging/shelf-life-policy";

    /** O envase de hoje: a validade recomendada é contada a partir dele. */
    private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);

    @Autowired WebApplicationContext context;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void recommendsShelfLifeFromTheMeasuredOxygenWithEvidence() throws Exception {
        var session = login();
        savePolicy(session).andExpect(status().isOk());
        var planId = executedPlan(session);

        // TPO de 80 ppb cai na faixa de até 100 ppb, que a casa diz sustentar 120 dias.
        measure(session, planId, "30", "80", true, true).andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendation.shelfLifeDays", is(120)))
                .andExpect(jsonPath("$.recommendation.bestBefore", is(TODAY.plusDays(120).toString())))
                .andExpect(jsonPath("$.recommendation.matchedTierMaxTpoPpb", is(100.0)))
                .andExpect(jsonPath("$.recommendation.withinPolicyTiers", is(true)))
                .andExpect(jsonPath("$.recommendation.caveats").isEmpty())
                // A evidência acompanha o número: é isso que torna a recomendação auditável.
                .andExpect(jsonPath("$.recommendation.factors[?(@.name=='tpo')].explanation").isNotEmpty())
                .andExpect(jsonPath("$.freshness.headspaceOxygenPpb", is(50)))
                .andExpect(jsonPath("$.freshness.evidenceComplete", is(true)));
    }

    @Test
    void unverifiedPurgeAndFailedSealBecomeCaveatsWithoutChangingTheNumber() throws Exception {
        var session = login();
        savePolicy(session).andExpect(status().isOk());
        var planId = executedPlan(session);

        measure(session, planId, "30", "80", false, false).andExpect(status().isOk())
                // Evidência incompleta não inventa outro número: reduz a confiança nele.
                .andExpect(jsonPath("$.recommendation.shelfLifeDays", is(120)))
                .andExpect(jsonPath("$.recommendation.caveats", hasItem(containsString("Purga não conferida"))))
                .andExpect(jsonPath("$.recommendation.caveats", hasItem(containsString("Vedação reprovada"))))
                .andExpect(jsonPath("$.freshness.evidenceComplete", is(false)));
    }

    @Test
    void oxygenAboveEveryTierFallsToTheWorstCaseAndSaysSo() throws Exception {
        var session = login();
        savePolicy(session).andExpect(status().isOk());
        var planId = executedPlan(session);

        measure(session, planId, "100", "400", true, true).andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendation.shelfLifeDays", is(30)))
                .andExpect(jsonPath("$.recommendation.withinPolicyTiers", is(false)))
                .andExpect(jsonPath("$.recommendation.caveats",
                        hasItem(containsString("acima de todas as faixas"))));
    }

    @Test
    void breweryWithoutPolicyHasNoShelfLifeRule() throws Exception {
        // O sistema não inventa a conversão de ppb em dias: sem política configurada não há regra.
        // O efeito disso sobre o registro (evidência mantida, validade em aberto) é coberto no
        // domínio, em ShelfLifeTest — aqui a cervejaria de teste é compartilhada entre os casos.
        var fresh = principal(UUID.randomUUID(), Set.of("packaging.plan.read"));

        mockMvc.perform(get(POLICY).with(authentication(fresh)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void overrideKeepsTheRecommendationAndIsAudited() throws Exception {
        var session = login();
        savePolicy(session).andExpect(status().isOk());
        var planId = executedPlan(session);
        measure(session, planId, "30", "80", true, true).andExpect(status().isOk());

        mockMvc.perform(post(PLANS + "/" + planId + "/freshness/override").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"shelfLifeDays\":180,\"reason\":\"lote destinado a estoque refrigerado\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get(PLANS + "/" + planId + "/freshness").session(session))
                .andExpect(status().isOk())
                // O recomendado nunca é sobrescrito: os dois ficam lado a lado.
                .andExpect(jsonPath("$.recommendedShelfLifeDays", is(120)))
                .andExpect(jsonPath("$.recommendedBestBefore", is(TODAY.plusDays(120).toString())))
                .andExpect(jsonPath("$.overrideShelfLifeDays", is(180)))
                .andExpect(jsonPath("$.overrideBestBefore", is(TODAY.plusDays(180).toString())))
                .andExpect(jsonPath("$.overrideReason", is("lote destinado a estoque refrigerado")))
                .andExpect(jsonPath("$.overriddenBy", notNullValue()))
                .andExpect(jsonPath("$.extendsBeyondRecommendation", is(true)))
                .andExpect(jsonPath("$.effectiveBestBefore", is(TODAY.plusDays(180).toString())));

        // O override fica no rastro de auditoria.
        mockMvc.perform(get("/api/v1/security/audit-events").session(session)
                        .param("action", "packaging.freshness.override"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].action", hasItem("packaging.freshness.override")));
    }

    @Test
    void overrideRequiresReasonAndPositiveShelfLife() throws Exception {
        var session = login();
        savePolicy(session).andExpect(status().isOk());
        var planId = executedPlan(session);
        measure(session, planId, "30", "80", true, true).andExpect(status().isOk());

        override(session, planId, 180, "").andExpect(status().isBadRequest());
        override(session, planId, 0, "motivo").andExpect(status().isBadRequest());

        mockMvc.perform(get(PLANS + "/" + planId + "/freshness").session(session))
                .andExpect(jsonPath("$.overrideBestBefore").doesNotExist());
    }

    @Test
    void remeasuringReplacesTheRecordAndDropsAStaleOverride() throws Exception {
        var session = login();
        savePolicy(session).andExpect(status().isOk());
        var planId = executedPlan(session);
        measure(session, planId, "30", "80", true, true).andExpect(status().isOk());
        override(session, planId, 180, "estoque refrigerado").andExpect(status().isOk());

        // Nova medição: a evidência mudou, então o override anterior não vale mais.
        measure(session, planId, "100", "400", true, true).andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendation.shelfLifeDays", is(30)));

        mockMvc.perform(get(PLANS + "/" + planId + "/freshness").session(session))
                .andExpect(jsonPath("$.recommendedShelfLifeDays", is(30)))
                .andExpect(jsonPath("$.overrideBestBefore").doesNotExist())
                .andExpect(jsonPath("$.effectiveShelfLifeDays", is(30)));
    }

    @Test
    void refusesTotalOxygenBelowDissolved() throws Exception {
        var session = login();
        var planId = executedPlan(session);

        // O total inclui o dissolvido: TPO < DO é erro de leitura ou de unidade.
        measure(session, planId, "80", "30", true, true).andExpect(status().isBadRequest());
        measure(session, planId, "-1", "80", true, true).andExpect(status().isBadRequest());
    }

    @Test
    void oxygenIsMeasuredOnlyAfterThePackagingRun() throws Exception {
        var session = login();
        var planId = reservedPlan(session);

        // Sem envase executado não há embalagem cheia para medir.
        measure(session, planId, "30", "80", true, true).andExpect(status().isConflict());
    }

    @Test
    void policyRefusesCurvesWhereMoreOxygenBuysMoreShelfLife() throws Exception {
        var session = login();

        mockMvc.perform(put(POLICY).session(session).with(csrf()).contentType("application/json")
                        .content("""
                                {"tiers":[{"maxTpoPpb":50,"shelfLifeDays":60},
                                          {"maxTpoPpb":200,"shelfLifeDays":180}],"fallbackDays":30}
                                """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put(POLICY).session(session).with(csrf()).contentType("application/json")
                        .content("{\"tiers\":[],\"fallbackDays\":30}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void policyIsReadBackSortedByOxygen() throws Exception {
        var session = login();
        mockMvc.perform(put(POLICY).session(session).with(csrf()).contentType("application/json")
                        .content("""
                                {"tiers":[{"maxTpoPpb":200,"shelfLifeDays":60},
                                          {"maxTpoPpb":50,"shelfLifeDays":180}],"fallbackDays":30}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get(POLICY).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tiers[0].maxTpoPpb", is(50.0)))
                .andExpect(jsonPath("$.tiers[0].shelfLifeDays", is(180)))
                .andExpect(jsonPath("$.tiers[1].maxTpoPpb", is(200.0)))
                .andExpect(jsonPath("$.fallbackDays", is(30)));
    }

    @Test
    void deniesManageAndPolicyWithoutPermission() throws Exception {
        var session = login();
        var planId = executedPlan(session);

        mockMvc.perform(put(PLANS + "/" + planId + "/freshness")
                        .with(authentication(principal(UUID.randomUUID(), Set.of("packaging.plan.read"))))
                        .with(csrf()).contentType("application/json").content(body("30", "80", true, true)))
                .andExpect(status().isForbidden());
        // Configurar a política é alçada própria: gerir plano não basta.
        mockMvc.perform(put(POLICY)
                        .with(authentication(principal(UUID.randomUUID(),
                                Set.of("packaging.plan.read", "packaging.plan.manage"))))
                        .with(csrf()).contentType("application/json")
                        .content("{\"tiers\":[{\"maxTpoPpb\":50,\"shelfLifeDays\":180}],\"fallbackDays\":30}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void isolatesByTenant() throws Exception {
        var session = login();
        savePolicy(session).andExpect(status().isOk());
        var planId = executedPlan(session);
        measure(session, planId, "30", "80", true, true).andExpect(status().isOk());

        var other = principal(UUID.randomUUID(),
                Set.of("packaging.plan.read", "packaging.plan.manage", "packaging.policy.manage"));
        mockMvc.perform(get(PLANS + "/" + planId + "/freshness").with(authentication(other)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(POLICY).with(authentication(other)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(PLANS + "/" + planId + "/freshness/override").with(authentication(other))
                        .with(csrf()).contentType("application/json")
                        .content("{\"shelfLifeDays\":180,\"reason\":\"invasão\"}"))
                .andExpect(status().isBadRequest());
    }

    // --- helpers ---

    /** Política da casa: quanto mais limpo o envase, mais dias ele sustenta. */
    private ResultActions savePolicy(MockHttpSession session) throws Exception {
        return mockMvc.perform(put(POLICY).session(session).with(csrf()).contentType("application/json")
                .content("""
                        {"tiers":[{"maxTpoPpb":50,"shelfLifeDays":180},
                                  {"maxTpoPpb":100,"shelfLifeDays":120},
                                  {"maxTpoPpb":200,"shelfLifeDays":60}],"fallbackDays":30}
                        """));
    }

    private ResultActions measure(MockHttpSession session, String planId, String doPpb, String tpoPpb,
            boolean purged, boolean sealed) throws Exception {
        return mockMvc.perform(put(PLANS + "/" + planId + "/freshness").session(session).with(csrf())
                .contentType("application/json").content(body(doPpb, tpoPpb, purged, sealed)));
    }

    private static String body(String doPpb, String tpoPpb, boolean purged, boolean sealed) {
        return """
                {"dissolvedOxygenPpb":%s,"totalPackageOxygenPpb":%s,"purgeMethod":"purga com CO₂",
                 "purgeVerified":%s,"sealCheckMethod":"recravação medida","sealCheckPassed":%s}
                """.formatted(doPpb, tpoPpb, purged, sealed);
    }

    private ResultActions override(MockHttpSession session, String planId, int days, String reason)
            throws Exception {
        return mockMvc.perform(post(PLANS + "/" + planId + "/freshness/override").session(session).with(csrf())
                .contentType("application/json")
                .content("{\"shelfLifeDays\":" + days + ",\"reason\":\"" + reason + "\"}"));
    }

    /** Plano com envase já executado: é nele que o oxigênio da embalagem é medido. */
    private String executedPlan(MockHttpSession session) throws Exception {
        var planId = reservedPlan(session);
        mockMvc.perform(post(PLANS + "/" + planId + "/execution").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"inputVolumeLiters\":284,\"producedUnits\":780,\"rejectedUnits\":12}"))
                .andExpect(status().isOk());
        return planId;
    }

    private String reservedPlan(MockHttpSession session) throws Exception {
        var batchId = fermentingBatch(session);
        var containerId = createIngredient(session, "PACKAGING", "UNIT",
                "{\"volumeMl\":\"355\",\"material\":\"lata\"}");
        receiveContainers(session, containerId, 1000);
        var lineId = createEquipment(session);
        releaseCleaning(session, lineId);

        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var content = """
                {"code":"ENV-%s","batchId":"%s","containerId":"%s","plannedUnits":800,"lineEquipmentId":"%s",
                 "plannedStart":"%s","plannedEnd":"%s"}
                """.formatted(sfx, batchId, containerId, lineId, PLANNED_START, PLANNED_END);
        var body = mockMvc.perform(post(PLANS).session(session).with(csrf()).contentType("application/json")
                        .content(content))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var planId = JSON.readTree(body).get("id").asText();

        for (var item : new String[] {"CONTAINER_INSPECTED", "SEAL_TEST", "GAS_SUPPLY"}) {
            mockMvc.perform(post(PLANS + "/" + planId + "/checklist").session(session).with(csrf())
                            .contentType("application/json").content("{\"item\":\"" + item + "\"}"))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post(PLANS + "/" + planId + "/reserve").session(session).with(csrf()))
                .andExpect(status().isOk());
        return planId;
    }

    private void releaseCleaning(MockHttpSession session, String equipmentId) throws Exception {
        var code = "CIP-" + UUID.randomUUID().toString().substring(0, 8);
        var step = "{\"sequence\":1,\"method\":\"CIP\",\"product\":\"soda\",\"concentrationMinPct\":1.0,"
                + "\"concentrationMaxPct\":3.0,\"tempMinC\":50,\"tempMaxC\":70,\"timeMinutes\":15,"
                + "\"evidenceRequired\":false}";
        var procedureId = idOf(mockMvc.perform(post("/api/v1/sanitation/procedures").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"" + code + "\",\"name\":\"CIP linha\",\"steps\":[" + step + "]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/v1/sanitation/procedures/" + procedureId + "/publish").session(session)
                        .with(csrf()))
                .andExpect(status().isOk());
        var cycleId = idOf(mockMvc.perform(post("/api/v1/sanitation/cycles").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"procedureCode\":\"" + code + "\",\"equipmentId\":\"" + equipmentId + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/v1/sanitation/cycles/" + cycleId + "/steps").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"sequence\":1,\"measuredConcentrationPct\":2.0,\"measuredTempC\":60,"
                                + "\"measuredTimeMinutes\":20}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/sanitation/cycles/" + cycleId + "/complete").session(session).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/sanitation/cycles/" + cycleId + "/verification").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"rinseOk\":true,\"visualOk\":true,\"atpRlu\":40,\"atpThreshold\":100,"
                                + "\"microOk\":true}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/sanitation/cycles/" + cycleId + "/release").session(session).with(csrf()))
                .andExpect(status().isNoContent());
    }

    private void receiveContainers(MockHttpSession session, String containerId, int quantity) throws Exception {
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var supplierId = idOf(mockMvc.perform(post("/api/v1/suppliers").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"Sup " + sfx + "\",\"code\":\"S-" + sfx + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/v1/inventory/lots").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"ingredientId\":\"" + containerId + "\",\"supplierId\":\"" + supplierId
                                + "\",\"quantity\":" + quantity + ",\"unit\":\"UNIT\",\"unitCost\":0.9,"
                                + "\"expiryDate\":\"2028-01-01\",\"inspection\":\"APPROVED\"}"))
                .andExpect(status().isCreated());
    }

    private String fermentingBatch(MockHttpSession session) throws Exception {
        var batchId = startedBatch(session);
        var fermenter = createEquipment(session);
        mockMvc.perform(post("/api/v1/production/batches/" + batchId + "/transfer").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"destinationEquipmentId\":\"" + fermenter + "\",\"volumeLiters\":390,"
                                + "\"ogSg\":1.052,\"lossesLiters\":8}"))
                .andExpect(status().isCreated());
        return batchId;
    }

    private String startedBatch(MockHttpSession session) throws Exception {
        var orderId = releasedOrder(session);
        mockMvc.perform(post("/api/v1/brew-orders/" + orderId + "/start").session(session).with(csrf()))
                .andExpect(status().isOk());
        var listBody = mockMvc.perform(get("/api/v1/production/batches").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (JsonNode node : JSON.readTree(listBody).get("content")) {
            if (node.get("orderId").asText().equals(orderId)) {
                return node.get("id").asText();
            }
        }
        throw new AssertionError("lote não encontrado para a ordem " + orderId);
    }

    private String releasedOrder(MockHttpSession session) throws Exception {
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var equipmentId = createEquipment(session);
        var maltId = createIngredient(session, "MALT", "KG", "{\"potentialSg\":\"1.037\",\"colorEbc\":\"4\"}");
        var hopId = createIngredient(session, "HOP", "G", "{\"alphaAcid\":\"12\"}");
        var yeastId = createIngredient(session, "YEAST", "UNIT", "{\"attenuation\":\"78\"}");
        var content = """
                {"name":"Fresh %s","equipmentId":"%s","batchVolumeLiters":400,"boilTimeMinutes":60,"targetIbu":30,
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

    private String createEquipment(MockHttpSession session) throws Exception {
        var code = "EQ-" + UUID.randomUUID().toString().substring(0, 8);
        return idOf(mockMvc.perform(post("/api/v1/equipment").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"" + code + "\",\"name\":\"Linha\",\"capacityLiters\":500,"
                                + "\"deadSpaceLiters\":20,\"mashEfficiencyPercent\":72,"
                                + "\"boilOffLitersPerHour\":8}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private String createIngredient(MockHttpSession session, String type, String unit, String attributes)
            throws Exception {
        var code = type.toLowerCase(java.util.Locale.ROOT).charAt(0) + "-"
                + UUID.randomUUID().toString().substring(0, 8);
        return idOf(mockMvc.perform(post("/api/v1/catalog/ingredients").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"type\":\"" + type + "\",\"code\":\"" + code + "\",\"name\":\"" + code
                                + "\",\"useUnit\":\"" + unit + "\",\"purchaseUnit\":\"" + unit
                                + "\",\"attributes\":" + attributes + "}"))
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
