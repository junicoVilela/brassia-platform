package br.com.brew.brassia.packaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
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
class PackagingRunIT {

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
    void executionDerivesLossesAndClosesTheBalance() throws Exception {
        var session = login();
        var scene = reservedPlan(session, 1000);

        // 780 boas + 12 rejeitadas de 355 ml = 281,16 L; saíram 284 L → 2,84 L de perda.
        execute(session, scene.planId, "284", 780, 12).andExpect(status().isOk())
                .andExpect(jsonPath("$.packagedVolumeLiters", is(276.9)))
                .andExpect(jsonPath("$.lossesLiters", is(2.84)))
                .andExpect(jsonPath("$.containersConsumed", is(792)));

        mockMvc.perform(get(PLANS + "/" + scene.planId + "/execution").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.producedUnits", is(780)))
                .andExpect(jsonPath("$.rejectedUnits", is(12)))
                .andExpect(jsonPath("$.rejectedVolumeLiters", is(4.26)))
                .andExpect(jsonPath("$.lossPercent", closeTo(1.0, 0.01)));

        mockMvc.perform(get(PLANS + "/" + scene.planId).session(session))
                .andExpect(jsonPath("$.status", is("EXECUTED")));
    }

    @Test
    void packagingConsumptionLeavesTheStockAndReleasesTheLeftoverReservation() throws Exception {
        var session = login();
        var scene = reservedPlan(session, 1000);

        // Reservou 800; 792 viraram lata (boas + rejeitos) e 8 voltam ao disponível.
        mockMvc.perform(get("/api/v1/inventory/lots/" + scene.lotId + "/balance").session(session))
                .andExpect(jsonPath("$.available", is(200.0)))
                .andExpect(jsonPath("$.onHand", is(1000.0)));

        execute(session, scene.planId, "284", 780, 12).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/inventory/lots/" + scene.lotId + "/balance").session(session))
                // Consumo é saída física: o on-hand caiu de verdade.
                .andExpect(jsonPath("$.onHand", is(208.0)))
                // Nada continua reservado: o plano executado não segura estoque.
                .andExpect(jsonPath("$.available", is(208.0)));

        mockMvc.perform(get("/api/v1/inventory/lots/" + scene.lotId + "/movements").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type=='CONSUMPTION')]").isNotEmpty());
    }

    @Test
    void refusesUnitsThatHoldMoreBeerThanLeftTheTank() throws Exception {
        var session = login();
        var scene = reservedPlan(session, 1000);

        // 800 unidades de 355 ml = 284 L, mas só saíram 280 L do tanque.
        execute(session, scene.planId, "280", 800, 0)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("volume_balance")))
                .andExpect(jsonPath("$.balance.inputVolumeLiters", is(280)))
                .andExpect(jsonPath("$.balance.packagedVolumeLiters", is(284.0)))
                .andExpect(jsonPath("$.balance.shortfallLiters", is(4.0)));

        // Recusa não consome embalagem nem executa o plano.
        mockMvc.perform(get("/api/v1/inventory/lots/" + scene.lotId + "/balance").session(session))
                .andExpect(jsonPath("$.onHand", is(1000.0)));
        mockMvc.perform(get(PLANS + "/" + scene.planId).session(session))
                .andExpect(jsonPath("$.status", is("RESERVED")));
    }

    @Test
    void rejectedUnitsCountAgainstTheBalanceAndConsumePackaging() throws Exception {
        var session = login();
        var scene = reservedPlan(session, 1000);

        // 780 boas cabem em 280 L; 780 + 30 rejeitadas não.
        execute(session, scene.planId, "280", 780, 30).andExpect(status().isConflict());

        execute(session, scene.planId, "284", 700, 92).andExpect(status().isOk())
                .andExpect(jsonPath("$.containersConsumed", is(792)));
    }

    @Test
    void refusesToPackageMoreThanTheBatchHasLeft() throws Exception {
        var session = login();
        var scene = reservedPlan(session, 1000);

        // O lote foi transferido com 390 L; pedir 400 estoura o que existiu no tanque.
        execute(session, scene.planId, "400", 800, 0)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("batch_volume_exceeded")))
                .andExpect(jsonPath("$.batchVolume.remainingLiters", is(390.0)))
                .andExpect(jsonPath("$.batchVolume.requestedLiters", is(400)));
    }

    @Test
    void onlyReservedPlanIsExecutedAndExecutionIsTerminal() throws Exception {
        var session = login();
        var scene = plannedOnly(session);

        // Plano ainda sem reserva não executa.
        execute(session, scene.planId, "100", 200, 0).andExpect(status().isConflict());

        var reserved = reservedPlan(session, 1000);
        execute(session, reserved.planId, "284", 780, 12).andExpect(status().isOk());
        // Executar de novo é conflito, não um segundo envase.
        execute(session, reserved.planId, "50", 100, 0).andExpect(status().isConflict());
        // Executado não é cancelável: desfazer produção não é cancelar plano.
        mockMvc.perform(post(PLANS + "/" + reserved.planId + "/cancel").session(session).with(csrf())
                        .contentType("application/json").content("{\"reason\":\"mudei de ideia\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void executionUsesFreeStockWhenTheRunSpentMoreThanReserved() throws Exception {
        var session = login();
        var scene = reservedPlan(session, 1000);

        // Reservou 800, mas a linha gastou 850 (com rejeitos): o excedente sai do saldo livre.
        execute(session, scene.planId, "310", 800, 50).andExpect(status().isOk())
                .andExpect(jsonPath("$.containersConsumed", is(850)));

        mockMvc.perform(get("/api/v1/inventory/lots/" + scene.lotId + "/balance").session(session))
                .andExpect(jsonPath("$.onHand", is(150.0)))
                .andExpect(jsonPath("$.available", is(150.0)));
    }

    @Test
    void reportsShortfallWhenThereIsNotEnoughPackagingForTheRun() throws Exception {
        var session = login();
        var scene = reservedPlan(session, 810);

        execute(session, scene.planId, "310", 800, 50)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("insufficient_packaging_stock")))
                .andExpect(jsonPath("$.shortfall.requested", is(850)));

        // Nada consumido: a recusa não deixa efeito parcial.
        mockMvc.perform(get("/api/v1/inventory/lots/" + scene.lotId + "/balance").session(session))
                .andExpect(jsonPath("$.onHand", is(810.0)));
    }

    @Test
    void batchCanBeSplitAcrossRunsWhileTheSumFits() throws Exception {
        var session = login();
        var batchId = fermentingBatch(session);
        var containerId = createIngredient(session, "PACKAGING", "UNIT",
                "{\"volumeMl\":\"355\",\"material\":\"lata\"}");
        receiveContainers(session, containerId, 2000);

        var first = reserveFor(session, batchId, containerId, 500);
        execute(session, first, "177.5", 500, 0).andExpect(status().isOk());

        var second = reserveFor(session, batchId, containerId, 500);
        execute(session, second, "177.5", 500, 0).andExpect(status().isOk());

        // 177,5 + 177,5 = 355 L de 390; um terceiro envase de 100 L estoura o lote.
        var third = reserveFor(session, batchId, containerId, 300);
        execute(session, third, "100", 280, 0)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("batch_volume_exceeded")))
                .andExpect(jsonPath("$.batchVolume.alreadyPackagedLiters", is(355.0)));
    }

    @Test
    void rejectsRunWithoutUnitsOrWithoutInput() throws Exception {
        var session = login();
        var scene = reservedPlan(session, 1000);

        execute(session, scene.planId, "284", 0, 0).andExpect(status().isBadRequest());
        execute(session, scene.planId, "0", 800, 0).andExpect(status().isBadRequest());
        execute(session, scene.planId, "284", -1, 0).andExpect(status().isBadRequest());
    }

    @Test
    void deniesManageWithoutPermission() throws Exception {
        var session = login();
        var scene = reservedPlan(session, 1000);

        mockMvc.perform(post(PLANS + "/" + scene.planId + "/execution")
                        .with(authentication(principal(UUID.randomUUID(), Set.of("packaging.plan.read"))))
                        .with(csrf()).contentType("application/json").content(body("284", 780, 12)))
                .andExpect(status().isForbidden());
    }

    @Test
    void isolatesByTenant() throws Exception {
        var session = login();
        var scene = reservedPlan(session, 1000);
        execute(session, scene.planId, "284", 780, 12).andExpect(status().isOk());

        var other = principal(UUID.randomUUID(), Set.of("packaging.plan.read", "packaging.plan.manage"));
        mockMvc.perform(get(PLANS + "/" + scene.planId + "/execution").with(authentication(other)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(PLANS + "/" + scene.planId + "/execution").with(authentication(other)).with(csrf())
                        .contentType("application/json").content(body("100", 100, 0)))
                .andExpect(status().isBadRequest());
    }

    // --- cenário ---

    private record Scene(String planId, String lotId) {}

    /** Plano reservado de 800 latas de 355 ml, com {@code stock} latas em estoque. */
    @Test
    void oEnvaseGeraLoteDeProdutoAcabado() throws Exception {
        var session = login();
        var scene = reservedPlan(session, 1000);

        var body = execute(session, scene.planId, "284", 780, 12).andExpect(status().isOk())
                // O código sai na resposta da execução: é o que vai impresso na embalagem.
                .andExpect(jsonPath("$.finishedLotCode", is(notNullValue())))
                .andReturn().getResponse().getContentAsString();
        var code = JSON.readTree(body).get("finishedLotCode").asText();

        // A suíte compartilha a cervejaria de bootstrap: filtrar pelo lote desta execução é o que
        // torna a asserção sobre ESTE envase, e não sobre o primeiro da lista.
        var batchId = batchOfPlan(session, scene.planId);
        var lote = finishedLotOf(session, batchId, code);
        // Só as unidades boas: as 12 rejeitadas consumiram lata e não viraram produto.
        assertThat(lote.get("units").asInt()).isEqualTo(780);
        assertThat(lote.get("volumeLiters").asDouble()).isEqualTo(276.9);
    }

    private JsonNode finishedLotOf(MockHttpSession session, String batchId, String code) throws Exception {
        var body = mockMvc.perform(get("/api/v1/packaging/finished-lots").param("batchId", batchId)
                        .session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (JsonNode lot : JSON.readTree(body)) {
            if (lot.get("code").asText().equals(code)) {
                return lot;
            }
        }
        throw new AssertionError("lote de produto acabado ausente: " + code);
    }

    @Test
    void doisEnvasesDoMesmoLoteGeramLotesDistintos() throws Exception {
        var session = login();
        var batchId = fermentingBatch(session);
        var containerId = createIngredient(session, "PACKAGING", "UNIT",
                "{\"volumeMl\":\"355\",\"material\":\"lata\"}");
        receiveContainers(session, containerId, 4000);

        var primeiro = reserveFor(session, batchId, containerId, 400);
        execute(session, primeiro, "100", 280, 0).andExpect(status().isOk());
        var segundo = reserveFor(session, batchId, containerId, 400);
        execute(session, segundo, "100", 280, 0).andExpect(status().isOk());

        var body = mockMvc.perform(get("/api/v1/packaging/finished-lots").param("batchId", batchId)
                        .session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var codigos = new java.util.ArrayList<String>();
        for (JsonNode lot : JSON.readTree(body)) {
            codigos.add(lot.get("code").asText());
        }

        // Foram latas diferentes em momentos diferentes: um recall pode atingir só uma das duas.
        assertThat(codigos).hasSize(2).doesNotHaveDuplicates();
        assertThat(codigos).allMatch(c -> c.contains("/"));
    }

    @Test
    void aGenealogiaDoLoteChegaAoProdutoAcabado() throws Exception {
        var session = login();
        var scene = reservedPlan(session, 1000);
        execute(session, scene.planId, "284", 780, 12).andExpect(status().isOk());
        var batchId = batchOfPlan(session, scene.planId);

        mockMvc.perform(get("/api/v1/traceability/genealogy").session(session)
                        .param("nodeType", "BATCH").param("nodeId", batchId).param("direction", "FORWARD"))
                .andExpect(status().isOk())
                // A cadeia para a frente não termina mais na execução (TRC-001-B).
                .andExpect(jsonPath("$.nodes[*].type", hasItem("FINISHED_LOT")))
                .andExpect(jsonPath("$.edges[*].kind", hasItem("lote de produto acabado")))
                // O que falta agora é o passo de fora da fábrica.
                .andExpect(jsonPath("$.gaps[*].expectedLink", hasItem("expedição e destino")));
    }

    private String batchOfPlan(MockHttpSession session, String planId) throws Exception {
        var body = mockMvc.perform(get(PLANS + "/" + planId).session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("batchId").asText();
    }

    private Scene reservedPlan(MockHttpSession session, int stock) throws Exception {
        var batchId = fermentingBatch(session);
        var containerId = createIngredient(session, "PACKAGING", "UNIT",
                "{\"volumeMl\":\"355\",\"material\":\"lata\"}");
        var lotId = receiveContainers(session, containerId, stock);
        return new Scene(reserveFor(session, batchId, containerId, 800), lotId);
    }

    private Scene plannedOnly(MockHttpSession session) throws Exception {
        var batchId = fermentingBatch(session);
        var containerId = createIngredient(session, "PACKAGING", "UNIT",
                "{\"volumeMl\":\"355\",\"material\":\"lata\"}");
        var lotId = receiveContainers(session, containerId, 1000);
        return new Scene(plan(session, batchId, containerId, createEquipment(session), 800), lotId);
    }

    /** Plano criado, checklist confirmado, linha limpa e embalagem reservada. */
    private String reserveFor(MockHttpSession session, String batchId, String containerId, int units)
            throws Exception {
        var lineId = createEquipment(session);
        releaseCleaning(session, lineId);
        var planId = plan(session, batchId, containerId, lineId, units);
        for (var item : new String[] {"CONTAINER_INSPECTED", "SEAL_TEST", "GAS_SUPPLY"}) {
            mockMvc.perform(post(PLANS + "/" + planId + "/checklist").session(session).with(csrf())
                            .contentType("application/json").content("{\"item\":\"" + item + "\"}"))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post(PLANS + "/" + planId + "/reserve").session(session).with(csrf()))
                .andExpect(status().isOk());
        return planId;
    }

    private String plan(MockHttpSession session, String batchId, String containerId, String lineId, int units)
            throws Exception {
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var content = """
                {"code":"ENV-%s","batchId":"%s","containerId":"%s","plannedUnits":%d,"lineEquipmentId":"%s",
                 "plannedStart":"%s","plannedEnd":"%s"}
                """.formatted(sfx, batchId, containerId, units, lineId, PLANNED_START, PLANNED_END);
        var body = mockMvc.perform(post(PLANS).session(session).with(csrf()).contentType("application/json")
                        .content(content))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("id").asText();
    }

    // --- helpers ---

    private ResultActions execute(MockHttpSession session, String planId, String input, int produced,
            int rejected) throws Exception {
        return mockMvc.perform(post(PLANS + "/" + planId + "/execution").session(session).with(csrf())
                .contentType("application/json").content(body(input, produced, rejected)));
    }

    private static String body(String input, int produced, int rejected) {
        return "{\"inputVolumeLiters\":" + input + ",\"producedUnits\":" + produced
                + ",\"rejectedUnits\":" + rejected + "}";
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

    private String receiveContainers(MockHttpSession session, String containerId, int quantity) throws Exception {
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var supplierId = idOf(mockMvc.perform(post("/api/v1/suppliers").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"Sup " + sfx + "\",\"code\":\"S-" + sfx + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        return idOf(mockMvc.perform(post("/api/v1/inventory/lots").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"ingredientId\":\"" + containerId + "\",\"supplierId\":\"" + supplierId
                                + "\",\"quantity\":" + quantity + ",\"unit\":\"UNIT\",\"unitCost\":0.9,"
                                + "\"expiryDate\":\"2028-01-01\",\"inspection\":\"APPROVED\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
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
                {"name":"Run %s","equipmentId":"%s","batchVolumeLiters":400,"boilTimeMinutes":60,"targetIbu":30,
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
