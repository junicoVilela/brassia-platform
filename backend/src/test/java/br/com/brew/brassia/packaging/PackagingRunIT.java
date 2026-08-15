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

    @Autowired org.springframework.jdbc.core.simple.JdbcClient jdbc;
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

    // --- SAL-001-B: liberação do lote e o que torna um lote vendável ---

    /**
     * Vendável é liberado pela qualidade, dentro da validade e sem quarentena — decisão do mantenedor
     * em 2026-08-15. Este teste percorre os três estados que o lote atravessa.
     */
    @Test
    void oLoteSoFicaVendavelDepoisDeLiberadoEComValidadeApurada() throws Exception {
        var session = login();
        var scene = reservedPlan(session, 1000);
        var body = execute(session, scene.planId, "284", 780, 12).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var code = JSON.readTree(body).get("finishedLotCode").asText();
        var batchId = batchOfPlan(session, scene.planId);
        var lotId = finishedLotOf(session, batchId, code).get("id").asText();

        // 1. Recém-envasado: falta assinatura. O impedimento é nomeado, e não um "não disponível".
        mockMvc.perform(get(LOTS + "/" + lotId + "/sale-status").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sellable", is(false)))
                .andExpect(jsonPath("$.blocker.code", is("not_released")));

        // 2. Liberado, mas sem evidência de oxigênio: validade desconhecida não é validade em dia.
        mockMvc.perform(post(LOTS + "/" + lotId + "/release").session(session).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(LOTS + "/" + lotId + "/sale-status").session(session))
                .andExpect(jsonPath("$.sellable", is(false)))
                .andExpect(jsonPath("$.blocker.code", is("shelf_life_unknown")));

        // 3. Com validade registrada, o lote passa a ser vendável.
        registraFrescor(session, scene.planId);
        mockMvc.perform(get(LOTS + "/" + lotId + "/sale-status").session(session))
                .andExpect(jsonPath("$.sellable", is(true)))
                .andExpect(jsonPath("$.blocker").doesNotExist())
                .andExpect(jsonPath("$.bestBefore", is(notNullValue())));
    }

    @Test
    void naoSeLiberaDuasVezes() throws Exception {
        // Sobrescrever trocaria o responsável e a data, e a auditoria deixaria de saber quem respondeu
        // pelo lote. A resposta diz quem liberou e quando.
        var session = login();
        var scene = reservedPlan(session, 1000);
        var lotId = loteAcabado(session, scene.planId);

        mockMvc.perform(post(LOTS + "/" + lotId + "/release").session(session).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(post(LOTS + "/" + lotId + "/release").session(session).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("lot_already_released")))
                .andExpect(jsonPath("$.releasedBy", is(notNullValue())))
                .andExpect(jsonPath("$.releasedAt", is(notNullValue())));
    }

    @Test
    void liberarExigeAlcadaPropriaDaQualidade() throws Exception {
        // Planejar e executar envase não dá o direito de afirmar que a cerveja pode ir ao cliente.
        var session = login();
        var scene = reservedPlan(session, 1000);
        var lotId = loteAcabado(session, scene.planId);

        mockMvc.perform(post(LOTS + "/" + lotId + "/release")
                        .with(authentication(principal(UUID.randomUUID(),
                                Set.of("packaging.plan.read", "packaging.plan.manage"))))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void oLoteVendavelApareceNoProdutoQueOVende() throws Exception {
        // O produto é o par (receita, embalagem), e é assim que ele encontra os lotes: o lote acabado
        // sabe de que lote de produção veio, e o lote de produção sabe a receita.
        var session = login();
        var scene = reservedPlan(session, 1000);
        var lotId = loteAcabado(session, scene.planId);
        var batchId = batchOfPlan(session, scene.planId);
        var recipeId = receitaDoLote(session, batchId);
        var containerId = embalagemDoLote(session, batchId);
        var produtoId = criaProduto(session, recipeId, containerId);

        // Antes da liberação, o produto não tem nada a prometer.
        mockMvc.perform(get("/api/v1/sales/products/" + produtoId + "/sellable-lots").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(0)));

        mockMvc.perform(post(LOTS + "/" + lotId + "/release").session(session).with(csrf()))
                .andExpect(status().isNoContent());
        registraFrescor(session, scene.planId);

        mockMvc.perform(get("/api/v1/sales/products/" + produtoId + "/sellable-lots").session(session))
                .andExpect(jsonPath("$[?(@.finishedLotId=='" + lotId + "')].units",
                        is(java.util.List.of(780))));
    }

    private static final String LOTS = "/api/v1/packaging/finished-lots";

    private String loteAcabado(MockHttpSession session, String planId) throws Exception {
        var body = execute(session, planId, "284", 780, 12).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var code = JSON.readTree(body).get("finishedLotCode").asText();
        return finishedLotOf(session, batchOfPlan(session, planId), code).get("id").asText();
    }

    /**
     * Evidência de oxigênio e a validade que sai dela (FSL-001).
     *
     * <p>Sem política de vida útil cadastrada não há recomendação, e a validade passa a ser decisão
     * humana registrada — que é o override. Os dois passos existem porque o sistema recusa inventar
     * prazo: ou há política que sustente, ou alguém assume por escrito.
     */
    private void registraFrescor(MockHttpSession session, String planId) throws Exception {
        mockMvc.perform(put(PLANS + "/" + planId + "/freshness").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"dissolvedOxygenPpb":30,"totalPackageOxygenPpb":50,
                                 "purgeMethod":"CO2 counter-pressure","purgeVerified":true,
                                 "sealCheckMethod":"torque","sealCheckPassed":true}
                                """))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(post(PLANS + "/" + planId + "/freshness/override").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"shelfLifeDays\":180,\"reason\":\"validade definida no teste\"}"))
                .andExpect(status().is2xxSuccessful());
    }

    private String receitaDoLote(MockHttpSession session, String batchId) throws Exception {
        var body = mockMvc.perform(get("/api/v1/production/batches/" + batchId).session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("recipeId").asText();
    }

    private String embalagemDoLote(MockHttpSession session, String batchId) throws Exception {
        var body = mockMvc.perform(get(LOTS).param("batchId", batchId).session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get(0).get("containerId").asText();
    }

    private String criaProduto(MockHttpSession session, String recipeId, String containerId)
            throws Exception {
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var body = mockMvc.perform(post("/api/v1/sales/products").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"sku\":\"SKU-" + sfx + "\",\"name\":\"Produto de teste\","
                                + "\"recipeId\":\"" + recipeId + "\","
                                + "\"containerId\":\"" + containerId + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("id").asText();
    }

    // --- SAL-002: pedido, reserva de lote e promessa de entrega ---

    @Test
    void oPedidoReservaOLoteEOEstoqueNaoEVendidoDuasVezes() throws Exception {
        // O critério transversal da sprint é literal: "concorrência não vende estoque duas vezes".
        // Aqui a prova é sequencial — o segundo pedido encontra o estoque já preso pelo primeiro.
        var session = login();
        var cena = cenaVendavel(session);

        pedido(session, cena, 700, null).andExpect(status().isCreated());

        // Sobram 80 das 780: o segundo pedido de 700 não cabe.
        pedido(session, cena, 700, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("insufficient_lot_stock")))
                .andExpect(jsonPath("$.available", is(80)));
    }

    @Test
    void oLoteReservadoSomeDaOfertaQuandoAcaba() throws Exception {
        // Sem isto a tela ofereceria 780 unidades de um lote com 780 já vendidas — e alguém prometeria
        // cerveja que já tem dono.
        var session = login();
        var cena = cenaVendavel(session);
        var url = "/api/v1/sales/products/" + cena.produtoId + "/sellable-lots";

        mockMvc.perform(get(url).session(session))
                .andExpect(jsonPath("$[0].units", is(780)))
                .andExpect(jsonPath("$[0].freeUnits", is(780)));

        pedido(session, cena, 700, null).andExpect(status().isCreated());

        // O lote continua existindo e continua vendável; o que mudou é quanto sobrou.
        mockMvc.perform(get(url).session(session))
                .andExpect(jsonPath("$[0].units", is(780)))
                .andExpect(jsonPath("$[0].freeUnits", is(80)));

        pedido(session, cena, 80, null).andExpect(status().isCreated());

        // Zerado, ele sai da oferta: mostrá-lo faria alguém prometer o que tem dono.
        mockMvc.perform(get(url).session(session))
                .andExpect(jsonPath("$.length()", is(0)));
    }

    @Test
    void aChaveDeIdempotenciaDevolveOMesmoPedido() throws Exception {
        // Um duplo clique ou um retry de rede não pode reservar o mesmo estoque duas vezes — o segundo
        // tiraria do próximo comprador uma cerveja que ninguém vai levar.
        var session = login();
        var cena = cenaVendavel(session);
        var chave = UUID.randomUUID().toString();

        var primeiro = pedidoComChave(session, cena, 100, chave)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var segundo = pedidoComChave(session, cena, 100, chave)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        assertThat(JSON.readTree(segundo).get("id").asText())
                .isEqualTo(JSON.readTree(primeiro).get("id").asText());

        // E o estoque foi tocado uma vez só: sobram 680, e não 580.
        pedido(session, cena, 680, null).andExpect(status().isCreated());
    }

    @Test
    void naoSePrometeEntregaDepoisDaValidadeDoLote() throws Exception {
        // A regra que dá nome à história, agora de ponta a ponta. A resposta traz as duas datas e o
        // lote, porque é o que resolve: sem isso, sobra tentativa e erro.
        var session = login();
        var cena = cenaVendavel(session);

        pedido(session, cena, 10, java.time.LocalDate.now().plusDays(5000))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("promise_after_shelf_life")))
                .andExpect(jsonPath("$.earliestBestBefore", is(notNullValue())))
                .andExpect(jsonPath("$.lotCode", is(notNullValue())));
    }

    @Test
    void cancelarDevolveOEstoque() throws Exception {
        var session = login();
        var cena = cenaVendavel(session);

        var body = pedido(session, cena, 780, null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var pedidoId = JSON.readTree(body).get("id").asText();

        // Com tudo reservado, não cabe mais nada.
        pedido(session, cena, 1, null).andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/sales/orders/" + pedidoId + "/cancel").session(session).with(csrf()))
                .andExpect(status().isNoContent());

        // Devolvido: cabe de novo.
        pedido(session, cena, 780, null).andExpect(status().isCreated());
    }

    @Test
    void oPedidoCongelaOPrecoEGuardaOLoteReservado() throws Exception {
        // O preço congelado é o que mantém um pedido de março explicável em dezembro; o lote reservado
        // é o que um recall percorre.
        var session = login();
        var cena = cenaVendavel(session);

        var body = pedido(session, cena, 10, null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var pedidoId = JSON.readTree(body).get("id").asText();

        mockMvc.perform(get("/api/v1/sales/orders/" + pedidoId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total", is(120.00)))
                .andExpect(jsonPath("$.currency", is("BRL")))
                .andExpect(jsonPath("$.lines[0].unitAmount", is(12.0000)))
                .andExpect(jsonPath("$.lines[0].reservations[0].lotCode", is(cena.lotCode)))
                .andExpect(jsonPath("$.lines[0].reservations[0].units", is(10)));
    }

    @Test
    void semPrecoNoCanalOPedidoERecusado() throws Exception {
        // "Ainda não precificado" e "de graça" são coisas opostas, e um total zero faria a venda sair
        // de graça.
        var session = login();
        var cena = cenaVendavel(session);
        var outroCanal = criaCanal(session);

        mockMvc.perform(post("/api/v1/sales/orders").session(session).with(csrf())
                        .contentType("application/json")
                        .content(corpoPedido(cena, outroCanal, 10, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("no_price_for_product")));
    }

    @Test
    void pedidoExigeAlcadaPropria() throws Exception {
        var session = login();
        var cena = cenaVendavel(session);

        mockMvc.perform(post("/api/v1/sales/orders")
                        .with(authentication(principal(UUID.randomUUID(), Set.of("sales.catalog.manage"))))
                        .with(csrf()).contentType("application/json")
                        .content(corpoPedido(cena, cena.canalId, 10, null)))
                .andExpect(status().isForbidden());
    }

    // --- cenário de venda ---

    private record CenaVenda(String produtoId, String canalId, String clienteId, String lotCode) {}

    /** Lote envasado, liberado, com validade, produto criado, canal e preço de R$ 12,00. */
    private CenaVenda cenaVendavel(MockHttpSession session) throws Exception {
        var scene = reservedPlan(session, 1000);
        var lotId = loteAcabado(session, scene.planId);
        mockMvc.perform(post(LOTS + "/" + lotId + "/release").session(session).with(csrf()))
                .andExpect(status().isNoContent());
        registraFrescor(session, scene.planId);

        var batchId = batchOfPlan(session, scene.planId);
        var produtoId = criaProduto(session, receitaDoLote(session, batchId),
                embalagemDoLote(session, batchId));
        var canalId = criaCanal(session);
        mockMvc.perform(post("/api/v1/sales/products/" + produtoId + "/prices").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"channelId\":\"" + canalId + "\",\"amount\":12.00,"
                                + "\"currency\":\"BRL\",\"taxIncluded\":false,"
                                + "\"validFrom\":\"" + java.time.LocalDate.now().minusDays(1) + "\"}"))
                .andExpect(status().isNoContent());

        var lotCode = mockMvc.perform(get(LOTS).param("batchId", batchId).session(session))
                .andReturn().getResponse().getContentAsString();
        return new CenaVenda(produtoId, canalId, criaCliente(session),
                JSON.readTree(lotCode).get(0).get("code").asText());
    }

    private String criaCanal(MockHttpSession session) throws Exception {
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var body = mockMvc.perform(post("/api/v1/sales/channels").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"CH-" + sfx + "\",\"name\":\"Canal de teste\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("id").asText();
    }

    private String criaCliente(MockHttpSession session) throws Exception {
        var body = mockMvc.perform(post("/api/v1/crm/customers").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"legalName\":\"Cliente de teste\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("id").asText();
    }

    private String corpoPedido(CenaVenda cena, String canalId, int quantidade,
            java.time.LocalDate promessa) {
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        return "{\"code\":\"PED-" + sfx + "\",\"customerId\":\"" + cena.clienteId + "\","
                + "\"channelId\":\"" + canalId + "\","
                + (promessa == null ? "" : "\"promisedFor\":\"" + promessa + "\",")
                + "\"items\":[{\"productId\":\"" + cena.produtoId + "\",\"quantity\":" + quantidade + "}]}";
    }

    private org.springframework.test.web.servlet.ResultActions pedido(MockHttpSession session,
            CenaVenda cena, int quantidade, java.time.LocalDate promessa) throws Exception {
        return mockMvc.perform(post("/api/v1/sales/orders").session(session).with(csrf())
                .contentType("application/json").content(corpoPedido(cena, cena.canalId, quantidade, promessa)));
    }

    private org.springframework.test.web.servlet.ResultActions pedidoComChave(MockHttpSession session,
            CenaVenda cena, int quantidade, String chave) throws Exception {
        return mockMvc.perform(post("/api/v1/sales/orders").session(session).with(csrf())
                .header("Idempotency-Key", chave)
                .contentType("application/json").content(corpoPedido(cena, cena.canalId, quantidade, null)));
    }

    // --- SAL-003: portal do cliente ---

    @Test
    void oPortalSoMostraOsPedidosDoProprioCliente() throws Exception {
        // O isolamento é estrutural: o cliente vem do vínculo do usuário, e nunca do caminho ou do
        // corpo. Se viesse de fora, bastaria trocá-lo para ver o pedido de outro.
        var session = login();
        var cena = cenaVendavel(session);
        var outroCliente = criaCliente(session);

        var body = pedido(session, cena, 10, null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var pedidoId = JSON.readTree(body).get("id").asText();

        var doDono = portalUser(session, cena.clienteId, cena.canalId);
        var doOutro = portalUser(session, outroCliente, cena.canalId);

        mockMvc.perform(get("/api/v1/portal/orders").with(authentication(doDono)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + pedidoId + "')].code", is(notNullValue())));

        // O outro cliente não vê nada — e o pedido específico responde 404, e não 403: distinguir
        // contaria que o identificador existe em algum lugar.
        mockMvc.perform(get("/api/v1/portal/orders").with(authentication(doOutro)))
                .andExpect(jsonPath("$[?(@.id=='" + pedidoId + "')]", is(java.util.List.of())));
        mockMvc.perform(get("/api/v1/portal/orders/" + pedidoId).with(authentication(doOutro)))
                .andExpect(status().isNotFound());
    }

    @Test
    void oUsuarioDePortalNaoAlcancaOsEndpointsInternos() throws Exception {
        // portal.access é a única permissão que ele recebe, e ela não abre nada interno.
        var session = login();
        var cena = cenaVendavel(session);
        var portal = portalUser(session, cena.clienteId, cena.canalId);

        mockMvc.perform(get("/api/v1/sales/orders").with(authentication(portal)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/crm/customers").with(authentication(portal)))
                .andExpect(status().isForbidden());
    }

    @Test
    void permissaoSemVinculoNaoAbreOPortal() throws Exception {
        // A permissão diz que ele pode entrar; o vínculo diz de quem ele é. Sem o segundo não há a
        // quem mostrar nada.
        var semVinculo = principal(UUID.randomUUID(), Set.of("portal.access"));

        mockMvc.perform(get("/api/v1/portal/catalog").with(authentication(semVinculo)))
                .andExpect(status().isForbidden());
    }

    @Test
    void oCatalogoDoPortalUsaOPrecoDoCanalDoVinculo() throws Exception {
        var session = login();
        var cena = cenaVendavel(session);
        var portal = portalUser(session, cena.clienteId, cena.canalId);

        mockMvc.perform(get("/api/v1/portal/catalog").with(authentication(portal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].unitAmount", is(12.0000)))
                .andExpect(jsonPath("$[0].currency", is("BRL")))
                .andExpect(jsonPath("$[0].availableUnits", is(780)));
    }

    @Test
    void oClienteFazOProprioPedidoPeloPortal() throws Exception {
        var session = login();
        var cena = cenaVendavel(session);
        var portal = portalUser(session, cena.clienteId, cena.canalId);

        mockMvc.perform(post("/api/v1/portal/orders").with(authentication(portal)).with(csrf())
                        .contentType("application/json")
                        .content(corpoPortal(cena, 10)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/portal/orders").with(authentication(portal)))
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].total", is(120.00)))
                // O cliente vê o que comprou, e não de qual brassa saiu: lote é rastro interno.
                .andExpect(jsonPath("$[0].lines[0].sku", is(notNullValue())));
    }

    @Test
    void oTetoDeCompromissoRecusaComOsTresNumeros() throws Exception {
        // Saber que "passou do limite" sem saber de quanto é o teto, quanto já está comprometido e
        // quanto este pedido pede deixa quem comprou sem ação — e no portal não há vendedor por perto.
        var session = login();
        var cena = cenaVendavel(session);
        var portal = portalUser(session, cena.clienteId, cena.canalId);

        mockMvc.perform(put("/api/v1/sales/portal/credit/" + cena.clienteId).session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"ceiling\":200.00,\"currency\":\"BRL\"}"))
                .andExpect(status().isNoContent());

        // 10 unidades a 12,00 = 120,00: cabe.
        mockMvc.perform(post("/api/v1/portal/orders").with(authentication(portal)).with(csrf())
                        .contentType("application/json").content(corpoPortal(cena, 10)))
                .andExpect(status().isCreated());

        // Mais 10 seriam 240,00 de compromisso, acima do teto de 200,00.
        mockMvc.perform(post("/api/v1/portal/orders").with(authentication(portal)).with(csrf())
                        .contentType("application/json").content(corpoPortal(cena, 10)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("credit_limit_exceeded")))
                .andExpect(jsonPath("$.ceiling", is(200.00)))
                .andExpect(jsonPath("$.committed", is(120.0000)))
                .andExpect(jsonPath("$.requested", is(120.0000)));
    }

    @Test
    void semTetoTudoCabe() throws Exception {
        // Não recusar por falta de decisão é reversível; recusar um pedido bom porque alguém chutou um
        // teto não é.
        var session = login();
        var cena = cenaVendavel(session);
        var portal = portalUser(session, cena.clienteId, cena.canalId);

        mockMvc.perform(post("/api/v1/portal/orders").with(authentication(portal)).with(csrf())
                        .contentType("application/json").content(corpoPortal(cena, 700)))
                .andExpect(status().isCreated());
    }

    @Test
    void aRecompraRepeteOsItensComOPrecoDeHoje() throws Exception {
        // Repete a intenção, e não o valor: reaproveitar o preço antigo faria a cervejaria vender
        // abaixo da lista sem ninguém ter decidido isso.
        var session = login();
        var cena = cenaVendavel(session);
        var portal = portalUser(session, cena.clienteId, cena.canalId);

        var body = mockMvc.perform(post("/api/v1/portal/orders").with(authentication(portal)).with(csrf())
                        .contentType("application/json").content(corpoPortal(cena, 10)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var primeiro = JSON.readTree(body).get("id").asText();

        // O preço sobe para 15,00 antes da recompra.
        mockMvc.perform(post("/api/v1/sales/products/" + cena.produtoId + "/prices").session(session)
                        .with(csrf()).contentType("application/json")
                        .content("{\"channelId\":\"" + cena.canalId + "\",\"amount\":15.00,"
                                + "\"currency\":\"BRL\",\"taxIncluded\":false,"
                                + "\"validFrom\":\"" + java.time.LocalDate.now() + "\"}"))
                .andExpect(status().isNoContent());

        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var recompra = mockMvc.perform(post("/api/v1/portal/orders/" + primeiro + "/reorder")
                        .with(authentication(portal)).with(csrf()).contentType("application/json")
                        .content("{\"code\":\"REC-" + sfx + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var novoId = JSON.readTree(recompra).get("id").asText();

        mockMvc.perform(get("/api/v1/portal/orders/" + novoId).with(authentication(portal)))
                .andExpect(jsonPath("$.total", is(150.00)))
                .andExpect(jsonPath("$.lines[0].quantity", is(10)));
    }

    /**
     * Um usuário de portal: identidade real com portal.access, e o vínculo gravado.
     *
     * <p>O usuário precisa existir em {@code security_user} — há chave estrangeira, e ela recusou o
     * identificador inventado da primeira versão deste teste. É a garantia funcionando: um vínculo de
     * portal para um usuário que não existe seria uma porta aberta para ninguém.
     */
    private Authentication portalUser(MockHttpSession session, String clienteId, String canalId)
            throws Exception {
        var userId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO security_user (id, email, normalized_email, display_name, status)
                VALUES (:id, :email, :email, 'Portal', 'ACTIVE')
                """)
                .param("id", userId)
                .param("email", "portal-" + userId + "@cliente.local")
                .update();
        mockMvc.perform(put("/api/v1/sales/portal/access/" + userId).session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"customerId\":\"" + clienteId + "\",\"channelId\":\"" + canalId + "\"}"))
                .andExpect(status().isNoContent());
        return principal(UUID.randomUUID(), Set.of("portal.access"), userId);
    }

    private String corpoPortal(CenaVenda cena, int quantidade) {
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        return "{\"code\":\"POR-" + sfx + "\",\"items\":[{\"productId\":\"" + cena.produtoId
                + "\",\"quantity\":" + quantidade + "}]}";
    }

    private Authentication principal(UUID breweryId, Set<String> permissions, UUID userId) {
        var p = new SecurityPrincipal(userId, breweryId, "Portal", permissions);
        return new UsernamePasswordAuthenticationToken(p, "n/a", Set.of());
    }
}
