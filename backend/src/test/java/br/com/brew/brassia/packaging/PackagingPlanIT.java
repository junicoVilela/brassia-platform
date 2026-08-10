package br.com.brew.brassia.packaging;

import static org.hamcrest.Matchers.containsInAnyOrder;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class PackagingPlanIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String PLANS = "/api/v1/packaging/plans";
    private static final String START = "2026-08-20T09:00:00Z";
    private static final String END = "2026-08-20T15:00:00Z";

    @Autowired WebApplicationContext context;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void planDerivesVolumeAndStartsWithPendingChecklist() throws Exception {
        var session = login();
        var scene = scene(session, 1000);

        var id = plan(session, scene, 800).andExpect(status().isCreated())
                // 800 × 355 ml = 284 L, derivado — o cliente não informa volume.
                .andExpect(jsonPath("$.plannedVolumeLiters", is(284.0)))
                .andReturn().getResponse().getContentAsString();
        var planId = JSON.readTree(id).get("id").asText();

        mockMvc.perform(get(PLANS + "/" + planId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PLANNED")))
                .andExpect(jsonPath("$.checklistComplete", is(false)))
                .andExpect(jsonPath("$.checklist.length()", is(3)))
                .andExpect(jsonPath("$.checklist[?(@.confirmed==true)].item").isEmpty());
    }

    @Test
    void reservesOnceChecklistIsCompleteAndLineIsClean() throws Exception {
        var session = login();
        var scene = scene(session, 1000);
        var planId = planned(session, scene, 800);

        confirmAll(session, planId);
        mockMvc.perform(post(PLANS + "/" + planId + "/reserve").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservedUnits", is(800)))
                .andExpect(jsonPath("$.unit", is("UNIT")));

        mockMvc.perform(get(PLANS + "/" + planId).session(session))
                .andExpect(jsonPath("$.status", is("RESERVED")))
                .andExpect(jsonPath("$.checklistComplete", is(true)));
        // A embalagem saiu do disponível do lote.
        mockMvc.perform(get("/api/v1/inventory/lots/" + scene.lotId + "/balance").session(session))
                .andExpect(jsonPath("$.available", is(200.0)));
    }

    @Test
    void listsEveryBlockerAtOnceInsteadOfOnePerAttempt() throws Exception {
        var session = login();
        // Linha nunca limpa e checklist intocado: os dois motivos vêm juntos.
        var scene = sceneWithDirtyLine(session, 1000);
        var planId = planned(session, scene, 800);

        mockMvc.perform(post(PLANS + "/" + planId + "/reserve").session(session).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("packaging_blocked")))
                .andExpect(jsonPath("$.blockers[*].code",
                        containsInAnyOrder("checklist_pending", "checklist_pending", "checklist_pending",
                                "line_not_clean")));

        // Nada foi reservado: o disponível do lote continua inteiro.
        mockMvc.perform(get("/api/v1/inventory/lots/" + scene.lotId + "/balance").session(session))
                .andExpect(jsonPath("$.available", is(1000.0)));
    }

    @Test
    void refusesDirtyLineEvenWithChecklistComplete() throws Exception {
        var session = login();
        var scene = sceneWithDirtyLine(session, 1000);
        var planId = planned(session, scene, 800);
        confirmAll(session, planId);

        mockMvc.perform(post(PLANS + "/" + planId + "/reserve").session(session).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.blockers[*].code", hasItem("line_not_clean")));
    }

    @Test
    void refusesLineUnderMaintenanceInThePlannedWindow() throws Exception {
        var session = login();
        var scene = scene(session, 1000);
        scheduleMaintenance(session, scene.lineId);
        var planId = planned(session, scene, 800);
        confirmAll(session, planId);

        mockMvc.perform(post(PLANS + "/" + planId + "/reserve").session(session).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.blockers[*].code", hasItem("line_under_maintenance")));
    }

    @Test
    void refusesTwoPlansOnTheSameLineAndWindow() throws Exception {
        var session = login();
        var scene = scene(session, 2000);
        var first = planned(session, scene, 500);
        confirmAll(session, first);
        mockMvc.perform(post(PLANS + "/" + first + "/reserve").session(session).with(csrf()))
                .andExpect(status().isOk());

        var second = planned(session, scene, 500);
        confirmAll(session, second);
        mockMvc.perform(post(PLANS + "/" + second + "/reserve").session(session).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.blockers[*].code", hasItem("line_conflict")));
    }

    @Test
    void reportsShortfallAndReservesNothingWhenPackagingIsMissing() throws Exception {
        var session = login();
        var scene = scene(session, 300);
        var planId = planned(session, scene, 800);
        confirmAll(session, planId);

        mockMvc.perform(post(PLANS + "/" + planId + "/reserve").session(session).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("insufficient_packaging_stock")))
                .andExpect(jsonPath("$.shortfall.requested", is(800)))
                .andExpect(jsonPath("$.shortfall.available", is(300.0)));

        mockMvc.perform(get(PLANS + "/" + planId).session(session))
                .andExpect(jsonPath("$.status", is("PLANNED")));
        mockMvc.perform(get("/api/v1/inventory/lots/" + scene.lotId + "/balance").session(session))
                .andExpect(jsonPath("$.available", is(300.0)));
    }

    @Test
    void repeatingChecklistAndReserveIsSafe() throws Exception {
        var session = login();
        var scene = scene(session, 1000);
        var planId = planned(session, scene, 800);

        confirmAll(session, planId);
        // Repetir a confirmação preserva a evidência original e não muda o estado.
        var before = confirmedAt(session, planId);
        confirmAll(session, planId);
        org.assertj.core.api.Assertions.assertThat(confirmedAt(session, planId)).isEqualTo(before);

        mockMvc.perform(post(PLANS + "/" + planId + "/reserve").session(session).with(csrf()))
                .andExpect(status().isOk());
        // Reservar de novo é conflito, não uma segunda reserva.
        mockMvc.perform(post(PLANS + "/" + planId + "/reserve").session(session).with(csrf()))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/v1/inventory/lots/" + scene.lotId + "/balance").session(session))
                .andExpect(jsonPath("$.available", is(200.0)));
    }

    @Test
    void cancellingReturnsThePackagingToStock() throws Exception {
        var session = login();
        var scene = scene(session, 1000);
        var planId = planned(session, scene, 800);
        confirmAll(session, planId);
        mockMvc.perform(post(PLANS + "/" + planId + "/reserve").session(session).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(post(PLANS + "/" + planId + "/cancel").session(session).with(csrf())
                        .contentType("application/json").content("{\"reason\":\"lote reprovado na análise\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get(PLANS + "/" + planId).session(session))
                .andExpect(jsonPath("$.status", is("CANCELLED")))
                .andExpect(jsonPath("$.cancelReason", is("lote reprovado na análise")));
        mockMvc.perform(get("/api/v1/inventory/lots/" + scene.lotId + "/balance").session(session))
                .andExpect(jsonPath("$.available", is(1000.0)));
        // Cancelado é terminal.
        mockMvc.perform(post(PLANS + "/" + planId + "/cancel").session(session).with(csrf())
                        .contentType("application/json").content("{\"reason\":\"de novo\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void cancelledPlanFreesTheLineForAnotherPlan() throws Exception {
        var session = login();
        var scene = scene(session, 2000);
        var first = planned(session, scene, 500);
        confirmAll(session, first);
        mockMvc.perform(post(PLANS + "/" + first + "/reserve").session(session).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post(PLANS + "/" + first + "/cancel").session(session).with(csrf())
                        .contentType("application/json").content("{\"reason\":\"reprogramado\"}"))
                .andExpect(status().isOk());

        var second = planned(session, scene, 500);
        confirmAll(session, second);
        mockMvc.perform(post(PLANS + "/" + second + "/reserve").session(session).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void refusesToPlanMoreThanTheBatchHoldsOrOnAWrongContainer() throws Exception {
        var session = login();
        var scene = scene(session, 5000);

        // Lote de 390 L; 1200 × 355 ml = 426 L não cabe.
        plan(session, scene, 1200).andExpect(status().isBadRequest());

        // Malte não é embalagem.
        var malt = createIngredient(session, "MALT", "KG", "{\"potentialSg\":\"1.037\",\"colorEbc\":\"4\"}");
        mockMvc.perform(post(PLANS).session(session).with(csrf()).contentType("application/json")
                        .content(planBody(scene.batchId, malt, scene.lineId, 10)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void capsThePlanByWhatWasTransferredNotByWhatWasOrdered() throws Exception {
        var session = login();
        // Ordem de 400 L, mas só 390 L chegaram ao fermentador: a transferência tem perdas.
        var batchId = fermentingBatch(session);
        var container = createIngredient(session, "PACKAGING", "UNIT",
                "{\"volumeMl\":\"500\",\"material\":\"garrafa\"}");
        receiveContainers(session, container, 2000);
        var line = createEquipment(session);
        releaseCleaning(session, line);

        // 800 × 500 ml = 400 L: cabe no volume planejado, mas não na cerveja que existe.
        mockMvc.perform(post(PLANS).session(session).with(csrf()).contentType("application/json")
                        .content(planBody(batchId, container, line, 800)))
                .andExpect(status().isBadRequest());

        // 780 × 500 ml = 390 L: exatamente o que foi transferido.
        mockMvc.perform(post(PLANS).session(session).with(csrf()).contentType("application/json")
                        .content(planBody(batchId, container, line, 780)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plannedVolumeLiters", is(390.0)));
    }

    @Test
    void refusesToPlanForABatchThatIsNotFermenting() throws Exception {
        var session = login();
        // Lote recém-aberto (IN_PROGRESS): ainda é dia de brassagem, não envase.
        var batchId = startedBatch(session);
        var container = createContainer(session);
        var line = createEquipment(session);

        mockMvc.perform(post(PLANS).session(session).with(csrf()).contentType("application/json")
                        .content(planBody(batchId, container, line, 100)))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsDuplicateCodeAndUnknownReferences() throws Exception {
        var session = login();
        var scene = scene(session, 1000);
        var code = "ENV-" + UUID.randomUUID().toString().substring(0, 8);

        mockMvc.perform(post(PLANS).session(session).with(csrf()).contentType("application/json")
                        .content(planBody(code, scene.batchId, scene.containerId, scene.lineId, 100)))
                .andExpect(status().isCreated());
        mockMvc.perform(post(PLANS).session(session).with(csrf()).contentType("application/json")
                        .content(planBody(code, scene.batchId, scene.containerId, scene.lineId, 100)))
                .andExpect(status().isConflict());

        // Lote e linha inexistentes.
        mockMvc.perform(post(PLANS).session(session).with(csrf()).contentType("application/json")
                        .content(planBody(UUID.randomUUID().toString(), scene.containerId, scene.lineId, 100)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(PLANS).session(session).with(csrf()).contentType("application/json")
                        .content(planBody(scene.batchId, scene.containerId, UUID.randomUUID().toString(), 100)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deniesManageWithoutPermission() throws Exception {
        var session = login();
        var scene = scene(session, 1000);
        var planId = planned(session, scene, 100);

        mockMvc.perform(post(PLANS + "/" + planId + "/reserve")
                        .with(authentication(principal(UUID.randomUUID(), Set.of("packaging.plan.read"))))
                        .with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(PLANS).with(authentication(principal(UUID.randomUUID(), Set.of()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void isolatesByTenant() throws Exception {
        var session = login();
        var scene = scene(session, 1000);
        var planId = planned(session, scene, 100);

        var other = principal(UUID.randomUUID(), Set.of("packaging.plan.read", "packaging.plan.manage"));
        mockMvc.perform(get(PLANS).with(authentication(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(0)));
        mockMvc.perform(get(PLANS + "/" + planId).with(authentication(other)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(PLANS + "/" + planId + "/reserve").with(authentication(other)).with(csrf()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(PLANS + "/" + planId + "/cancel").with(authentication(other)).with(csrf())
                        .contentType("application/json").content("{\"reason\":\"invasão\"}"))
                .andExpect(status().isBadRequest());
    }

    // --- cenário ---

    /** Referências de um envase possível: lote fermentando, embalagem em estoque e linha limpa. */
    private record Scene(String batchId, String containerId, String lotId, String lineId) {}

    private Scene scene(MockHttpSession session, int containersInStock) throws Exception {
        var scene = sceneWithDirtyLine(session, containersInStock);
        releaseCleaning(session, scene.lineId);
        return scene;
    }

    /** Mesmo cenário, mas a linha nunca teve ciclo de limpeza liberado. */
    private Scene sceneWithDirtyLine(MockHttpSession session, int containersInStock) throws Exception {
        var batchId = fermentingBatch(session);
        var containerId = createContainer(session);
        var lotId = receiveContainers(session, containerId, containersInStock);
        return new Scene(batchId, containerId, lotId, createEquipment(session));
    }

    // --- helpers ---

    private ResultActions plan(MockHttpSession session, Scene scene, int units) throws Exception {
        return mockMvc.perform(post(PLANS).session(session).with(csrf()).contentType("application/json")
                .content(planBody(scene.batchId, scene.containerId, scene.lineId, units)));
    }

    private String planned(MockHttpSession session, Scene scene, int units) throws Exception {
        var body = plan(session, scene, units).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("id").asText();
    }

    private static String planBody(String batchId, String containerId, String lineId, int units) {
        return planBody("ENV-" + UUID.randomUUID().toString().substring(0, 8), batchId, containerId, lineId, units);
    }

    private static String planBody(String code, String batchId, String containerId, String lineId, int units) {
        return """
                {"code":"%s","batchId":"%s","containerId":"%s","plannedUnits":%d,"lineEquipmentId":"%s",
                 "plannedStart":"%s","plannedEnd":"%s"}
                """.formatted(code, batchId, containerId, units, lineId, START, END);
    }

    private void confirmAll(MockHttpSession session, String planId) throws Exception {
        for (var item : new String[] {"CONTAINER_INSPECTED", "SEAL_TEST", "GAS_SUPPLY"}) {
            mockMvc.perform(post(PLANS + "/" + planId + "/checklist").session(session).with(csrf())
                            .contentType("application/json").content("{\"item\":\"" + item + "\"}"))
                    .andExpect(status().isOk());
        }
    }

    private String confirmedAt(MockHttpSession session, String planId) throws Exception {
        var body = mockMvc.perform(get(PLANS + "/" + planId).session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("checklist").get(0).get("confirmedAt").asText();
    }

    private void scheduleMaintenance(MockHttpSession session, String equipmentId) throws Exception {
        mockMvc.perform(post("/api/v1/equipment/" + equipmentId + "/maintenance").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"kind\":\"MAINTENANCE\",\"startAt\":\"2026-08-20T10:00:00Z\","
                                + "\"endAt\":\"2026-08-20T12:00:00Z\",\"notes\":\"troca de bico\"}"))
                .andExpect(status().isCreated());
    }

    /** Executa e libera um ciclo de limpeza no equipamento — é a evidência que o envase exige. */
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

    private String createContainer(MockHttpSession session) throws Exception {
        return createIngredient(session, "PACKAGING", "UNIT", "{\"volumeMl\":\"355\",\"material\":\"lata\"}");
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

    /** Lote transferido para o fermentador: é o estado em que envasar faz sentido. */
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
                {"name":"Envase %s","equipmentId":"%s","batchVolumeLiters":400,"boilTimeMinutes":60,"targetIbu":30,
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
                                + "\"deadSpaceLiters\":20,\"mashEfficiencyPercent\":72,\"boilOffLitersPerHour\":8}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private String createIngredient(MockHttpSession session, String type, String unit, String attributes)
            throws Exception {
        var code = type.toLowerCase(java.util.Locale.ROOT).charAt(0) + "-"
                + UUID.randomUUID().toString().substring(0, 8);
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
