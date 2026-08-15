package br.com.brew.brassia.packaging;

import static org.hamcrest.Matchers.closeTo;
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
import java.util.Set;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
class CarbonationIT {

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

    @Autowired WebApplicationContext context;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void previewExplainsInputsMethodAndVersionWithoutRecording() throws Exception {
        var session = login();
        var planId = plannedPlan(session);

        mockMvc.perform(get(PLANS + "/" + planId + "/carbonation/preview").session(session)
                        .param("method", "PRIMING").param("targetVolumes", "2.4")
                        .param("referenceTempC", "20").param("primingSugar", "SUCROSE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.residualVolumes", closeTo(0.86, 0.03)))
                .andExpect(jsonPath("$.missingVolumes", closeTo(1.54, 0.03)))
                // 800 latas de 355 ml = 284 L; o volume vem do plano, não é digitado de novo.
                .andExpect(jsonPath("$.beerVolumeLiters", is(284.0)))
                .andExpect(jsonPath("$.primingSugarGrams", closeTo(1667.0, 15.0)))
                .andExpect(jsonPath("$.calculatorVersion", notNullValue()))
                .andExpect(jsonPath("$.calculationMethod", notNullValue()))
                .andExpect(jsonPath("$.assumptions").isNotEmpty());

        // Prévia não grava nada.
        mockMvc.perform(get(PLANS + "/" + planId + "/carbonation").session(session))
                .andExpect(status().isBadRequest());
    }

    @Test
    void recordsPrimingDecisionWithConfirmation() throws Exception {
        var session = login();
        var planId = plannedPlan(session);

        carbonate(session, planId, "PRIMING", "2.4", "20", "SUCROSE", true)
                .andExpect(status().isOk());

        mockMvc.perform(get(PLANS + "/" + planId + "/carbonation").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.method", is("PRIMING")))
                .andExpect(jsonPath("$.targetVolumes", is(2.4)))
                .andExpect(jsonPath("$.referenceTempC", is(20.0)))
                .andExpect(jsonPath("$.residualVolumes", closeTo(0.86, 0.03)))
                .andExpect(jsonPath("$.primingSugar", is("SUCROSE")))
                .andExpect(jsonPath("$.primingSugarGrams", closeTo(1667.0, 15.0)))
                .andExpect(jsonPath("$.pressureBar").doesNotExist())
                .andExpect(jsonPath("$.confirmedBy", notNullValue()))
                .andExpect(jsonPath("$.calculatorVersion", notNullValue()));
    }

    @Test
    void refusesToRecordWithoutExplicitConfirmation() throws Exception {
        var session = login();
        var planId = plannedPlan(session);

        carbonate(session, planId, "PRIMING", "2.4", "20", "SUCROSE", false)
                .andExpect(status().isBadRequest());

        mockMvc.perform(get(PLANS + "/" + planId + "/carbonation").session(session))
                .andExpect(status().isBadRequest());
    }

    @Test
    void recordsForcedCarbonationPressure() throws Exception {
        var session = login();
        var planId = plannedPlan(session);

        carbonate(session, planId, "FORCED", "2.5", "4", null, true).andExpect(status().isOk());

        mockMvc.perform(get(PLANS + "/" + planId + "/carbonation").session(session))
                .andExpect(jsonPath("$.method", is("FORCED")))
                // Tabela de carbonatação: 2.5 vol a 4 °C ≈ 0.81 bar.
                .andExpect(jsonPath("$.pressureBar", closeTo(0.81, 0.05)))
                .andExpect(jsonPath("$.primingSugar").doesNotExist())
                .andExpect(jsonPath("$.primingSugarGrams").doesNotExist());
    }

    @Test
    void temperatureChangesTheResultInBothMethods() throws Exception {
        var session = login();
        var planId = plannedPlan(session);

        var cold = previewValue(session, planId, "PRIMING", "2.4", "4", "SUCROSE", "primingSugarGrams");
        var warm = previewValue(session, planId, "PRIMING", "2.4", "20", "SUCROSE", "primingSugarGrams");
        // Cerveja fria retém mais CO₂, então precisa de menos açúcar.
        org.assertj.core.api.Assertions.assertThat(cold).isLessThan(warm);

        var coldPressure = previewValue(session, planId, "FORCED", "2.5", "4", null, "pressureBar");
        var warmPressure = previewValue(session, planId, "FORCED", "2.5", "18", null, "pressureBar");
        // Cerveja quente exige mais pressão para o mesmo alvo.
        org.assertj.core.api.Assertions.assertThat(warmPressure).isGreaterThan(coldPressure);
    }

    @Test
    void refusesPrimingWhenResidualAlreadyReachesTheTarget() throws Exception {
        var session = login();
        var planId = plannedPlan(session);

        // A 4 °C sobram ~1.48 vol dissolvidos: pedir 1.2 vol com açúcar é sobrepressão.
        carbonate(session, planId, "PRIMING", "1.2", "4", "SUCROSE", true)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("over_carbonation")))
                .andExpect(jsonPath("$.carbonation.targetVolumes", is(1.2)))
                .andExpect(jsonPath("$.carbonation.residualVolumes", closeTo(1.48, 0.03)));

        mockMvc.perform(get(PLANS + "/" + planId + "/carbonation").session(session))
                .andExpect(status().isBadRequest());
    }

    @Test
    void forcedCarbonationBelowResidualIsAllowedWithoutPressure() throws Exception {
        var session = login();
        var planId = plannedPlan(session);

        // Sem adicionar nada à cerveja não há risco de estourar: a resposta é "não aplique pressão".
        carbonate(session, planId, "FORCED", "1.0", "0", null, true).andExpect(status().isOk());

        mockMvc.perform(get(PLANS + "/" + planId + "/carbonation").session(session))
                .andExpect(jsonPath("$.pressureBar", is(0.0)))
                .andExpect(jsonPath("$.alerts").isNotEmpty());
    }

    @Test
    void dryMaltExtractCarriesAConfidenceWarning() throws Exception {
        var session = login();
        var planId = plannedPlan(session);

        carbonate(session, planId, "PRIMING", "2.4", "20", "DRY_MALT_EXTRACT", true).andExpect(status().isOk());

        mockMvc.perform(get(PLANS + "/" + planId + "/carbonation").session(session))
                .andExpect(jsonPath("$.primingSugar", is("DRY_MALT_EXTRACT")))
                .andExpect(jsonPath("$.alerts", hasItem(org.hamcrest.Matchers.containsString("estimado"))));
    }

    @Test
    void recalculatingReplacesTheWholeDecision() throws Exception {
        var session = login();
        var planId = plannedPlan(session);

        carbonate(session, planId, "PRIMING", "2.4", "20", "SUCROSE", true).andExpect(status().isOk());
        carbonate(session, planId, "FORCED", "2.5", "4", null, true).andExpect(status().isOk());

        // Trocar de método não deixa resíduo do anterior.
        mockMvc.perform(get(PLANS + "/" + planId + "/carbonation").session(session))
                .andExpect(jsonPath("$.method", is("FORCED")))
                .andExpect(jsonPath("$.primingSugar").doesNotExist())
                .andExpect(jsonPath("$.primingSugarGrams").doesNotExist())
                .andExpect(jsonPath("$.pressureBar", closeTo(0.81, 0.05)));
    }

    @Test
    void cancelledPlanDoesNotAcceptCarbonation() throws Exception {
        var session = login();
        var planId = plannedPlan(session);
        mockMvc.perform(post(PLANS + "/" + planId + "/cancel").session(session).with(csrf())
                        .contentType("application/json").content("{\"reason\":\"lote reprovado\"}"))
                .andExpect(status().isOk());

        carbonate(session, planId, "PRIMING", "2.4", "20", "SUCROSE", true).andExpect(status().isConflict());
    }

    @Test
    void rejectsInvalidMethodSugarAndTarget() throws Exception {
        var session = login();
        var planId = plannedPlan(session);

        carbonate(session, planId, "NENHUM", "2.4", "20", "SUCROSE", true).andExpect(status().isBadRequest());
        carbonate(session, planId, "PRIMING", "2.4", "20", "ACUCAR_MASCAVO", true)
                .andExpect(status().isBadRequest());
        carbonate(session, planId, "PRIMING", "0", "20", "SUCROSE", true).andExpect(status().isBadRequest());
        // Priming sem açúcar informado não calcula.
        carbonate(session, planId, "PRIMING", "2.4", "20", null, true).andExpect(status().isBadRequest());
    }

    @Test
    void deniesManageWithoutPermission() throws Exception {
        var session = login();
        var planId = plannedPlan(session);

        mockMvc.perform(put(PLANS + "/" + planId + "/carbonation")
                        .with(authentication(principal(UUID.randomUUID(), Set.of("packaging.plan.read"))))
                        .with(csrf()).contentType("application/json")
                        .content(body("PRIMING", "2.4", "20", "SUCROSE", true)))
                .andExpect(status().isForbidden());
    }

    @Test
    void isolatesByTenant() throws Exception {
        var session = login();
        var planId = plannedPlan(session);
        carbonate(session, planId, "PRIMING", "2.4", "20", "SUCROSE", true).andExpect(status().isOk());

        var other = principal(UUID.randomUUID(), Set.of("packaging.plan.read", "packaging.plan.manage"));
        mockMvc.perform(get(PLANS + "/" + planId + "/carbonation").with(authentication(other)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(PLANS + "/" + planId + "/carbonation/preview").with(authentication(other))
                        .param("method", "PRIMING").param("targetVolumes", "2.4")
                        .param("referenceTempC", "20").param("primingSugar", "SUCROSE"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put(PLANS + "/" + planId + "/carbonation").with(authentication(other)).with(csrf())
                        .contentType("application/json").content(body("PRIMING", "2.4", "20", "SUCROSE", true)))
                .andExpect(status().isBadRequest());
    }

    // --- helpers ---

    private ResultActions carbonate(MockHttpSession session, String planId, String method, String target,
            String tempC, String sugar, boolean confirmed) throws Exception {
        return mockMvc.perform(put(PLANS + "/" + planId + "/carbonation").session(session).with(csrf())
                .contentType("application/json").content(body(method, target, tempC, sugar, confirmed)));
    }

    private static String body(String method, String target, String tempC, String sugar, boolean confirmed) {
        return """
                {"method":"%s","targetVolumes":%s,"referenceTempC":%s,%s"confirmed":%s}
                """.formatted(method, target, tempC,
                sugar == null ? "" : "\"primingSugar\":\"" + sugar + "\",", confirmed);
    }

    private double previewValue(MockHttpSession session, String planId, String method, String target,
            String tempC, String sugar, String field) throws Exception {
        var request = get(PLANS + "/" + planId + "/carbonation/preview").session(session)
                .param("method", method).param("targetVolumes", target).param("referenceTempC", tempC);
        if (sugar != null) {
            request = request.param("primingSugar", sugar);
        }
        var body = mockMvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get(field).asDouble();
    }

    @Test
    @DisplayName("ALVO ACIMA DO QUE A LATA SUPORTA É RECUSADO, com a pressão, o limite e a temperatura")
    void recusaAlvoAcimaDoLimiteDaEmbalagem() throws Exception {
        // PKG-002-A. O sistema já barrava o caso claro (priming sem espaço para o alvo) e deixava passar
        // o perigoso: alvo alto em embalagem frágil. Lata estourando é acidente de trabalho.
        var session = login();
        var planId = plannedPlan(session, "2.5");

        carbonate(session, planId, "FORCED", "3.2", "20", null, true)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("container_pressure_exceeded")))
                .andExpect(jsonPath("$.maxPressureBar", is(2.5)))
                .andExpect(jsonPath("$.expectedPressureBar").exists())
                // Sem a temperatura, quem opera não sabe que estocar mais quente piora o número.
                .andExpect(jsonPath("$.referenceTempC", is(20)));
    }

    @Test
    @DisplayName("A REGRA VALE NO PRIMING TAMBÉM: a física não pergunta de onde veio o CO₂")
    void recusaPrimingAcimaDoLimite() throws Exception {
        // É o caso que mais importa: garrafa com açúcar demais é a bomba clássica. A pressão de
        // equilíbrio é a mesma conta nos dois métodos.
        var session = login();
        var planId = plannedPlan(session, "2.5");

        carbonate(session, planId, "PRIMING", "3.2", "20", "SUCROSE", true)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("container_pressure_exceeded")));
    }

    @Test
    @DisplayName("alvo dentro do limite passa, e o alerta diz a pressão contra o limite")
    void dentroDoLimitePassaComAlerta() throws Exception {
        var session = login();
        var planId = plannedPlan(session, "5.0");

        carbonate(session, planId, "FORCED", "2.4", "4", null, true)
                .andExpect(status().isOk());

        var body = mockMvc.perform(get(PLANS + "/" + planId + "/carbonation").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(body).contains("limite de 5.0 bar");
    }

    @Test
    @DisplayName("SEM LIMITE CADASTRADO, O ALERTA DIZ QUE NADA FOI CONFERIDO")
    void semLimiteAvisaQueNaoConferiu() throws Exception {
        // Ausência declarada: é melhor dizer que não conferiu do que deixar quem opera supor que sim.
        var session = login();
        var planId = plannedPlan(session);

        carbonate(session, planId, "FORCED", "2.4", "4", null, true)
                .andExpect(status().isOk());

        var body = mockMvc.perform(get(PLANS + "/" + planId + "/carbonation").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(body).contains("não tem pressão máxima cadastrada");
    }

    /** Plano de envase de 800 latas de 355 ml (284 L) para um lote em fermentação. */
    private String plannedPlan(MockHttpSession session) throws Exception {
        return plannedPlan(session, null);
    }

    /**
     * O mesmo plano, com limite de pressão da embalagem quando informado (PKG-002-A).
     *
     * <p>Sem limite cadastrado o sistema não recusa nada — é o estado em que a plataforma estava, e os
     * testes antigos continuam descrevendo esse mundo.
     */
    private String plannedPlan(MockHttpSession session, String maxPressureBar) throws Exception {
        var batchId = fermentingBatch(session);
        var containerId = createIngredient(session, "PACKAGING", "UNIT",
                maxPressureBar == null
                        ? "{\"volumeMl\":\"355\",\"material\":\"lata\"}"
                        : "{\"volumeMl\":\"355\",\"material\":\"lata\",\"maxPressureBar\":\""
                                + maxPressureBar + "\"}");
        var lineId = createEquipment(session);
        var content = """
                {"code":"ENV-%s","batchId":"%s","containerId":"%s","plannedUnits":800,"lineEquipmentId":"%s",
                 "plannedStart":"%s","plannedEnd":"%s"}
                """.formatted(UUID.randomUUID().toString().substring(0, 8), batchId, containerId, lineId, PLANNED_START, PLANNED_END);
        var body = mockMvc.perform(post(PLANS).session(session).with(csrf()).contentType("application/json")
                        .content(content))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("id").asText();
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
                {"name":"Carb %s","equipmentId":"%s","batchVolumeLiters":400,"boilTimeMinutes":60,"targetIbu":30,
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
