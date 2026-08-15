package br.com.brew.brassia.traceability;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
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
import java.util.Locale;
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

/**
 * Recall de ponta a ponta (FDS-003), com a expedição que a TRC-001-D trouxe.
 *
 * <p>O que estes testes fixam é a divisão entre as duas metades do dossiê: o escopo, recalculado a
 * cada leitura, e a comunicação, que é registro do que a cervejaria fez. Um lote que sai <em>depois</em>
 * da abertura aparece como destino descoberto, e não entra calado na lista dos avisados.
 */
@SpringBootTest
@Testcontainers
class RecallIT {

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
    private static final String RECALLS = "/api/v1/traceability/recalls";
    private static final String SHIPMENTS = "/api/v1/packaging/shipments";
    private static final String PLANS = "/api/v1/packaging/plans";

    @Autowired WebApplicationContext context;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("abrir recall lista os destinos alcançados, um por expedição")
    void abrirRecallListaOsDestinos() throws Exception {
        var session = login();
        var scene = shippedLot(session);
        ship(session, scene.finishedLotId, "Distribuidora Norte", "(11) 98888-0000", 100)
                .andExpect(status().isCreated());

        var body = openRecall(session, "BATCH", scene.batchId, "contaminação confirmada na contraprova")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code", startsWith("REC-")))
                .andExpect(jsonPath("$.status", is("OPEN")))
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(get(RECALLS + "/" + idOf(body)).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications.length()", is(2)))
                .andExpect(jsonPath("$.notifications[*].destination", hasItem("Bar do Zé")))
                .andExpect(jsonPath("$.notifications[*].destination", hasItem("Distribuidora Norte")))
                // Nasce tudo pendente: um dossiê que nasce "tudo avisado" mente.
                .andExpect(jsonPath("$.pending", is(2)))
                .andExpect(jsonPath("$.coverage", is(0)))
                .andExpect(jsonPath("$.scope[*].node.type", hasItem("FINISHED_LOT")))
                .andExpect(jsonPath("$.scope[*].node.type", hasItem("SHIPMENT")));
    }

    @Test
    @DisplayName("registrar comunicação move a cobertura e libera o encerramento")
    void comunicarEEncerrar() throws Exception {
        var session = login();
        var scene = shippedLot(session);
        var recallId = idOf(openRecall(session, "BATCH", scene.batchId, "risco de vidro na embalagem")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        // Encerrar com destino pendente é recusado: o dossiê declararia terminado o que não terminou.
        close(session, recallId, "recolhido")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("recall_has_pending_notifications")))
                .andExpect(jsonPath("$.pending", is(1)));

        var notificationId = firstNotificationId(session, recallId);
        notify(session, recallId, notificationId, "telefone", "falei com o gerente; 40 latas devolvidas")
                .andExpect(status().isOk());

        mockMvc.perform(get(RECALLS + "/" + recallId).session(session))
                .andExpect(jsonPath("$.pending", is(0)))
                .andExpect(jsonPath("$.coverage", is(100)))
                .andExpect(jsonPath("$.notifications[0].channel", is("telefone")));

        close(session, recallId, "1 destino comunicado, 40 latas recolhidas").andExpect(status().isOk());

        mockMvc.perform(get(RECALLS + "/" + recallId).session(session))
                .andExpect(jsonPath("$.recall.status", is("CLOSED")));
        // Recall encerrado não recebe comunicação nova: a operação acabou.
        notify(session, recallId, notificationId, "e-mail", null).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("expedição feita depois da abertura aparece como destino descoberto")
    void expedicaoPosteriorApareceComoDescoberta() throws Exception {
        var session = login();
        var scene = shippedLot(session);
        var recallId = idOf(openRecall(session, "BATCH", scene.batchId, "desvio microbiológico")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        ship(session, scene.finishedLotId, "Mercado Central", "(11) 97777-0000", 50)
                .andExpect(status().isCreated());

        mockMvc.perform(get(RECALLS + "/" + recallId).session(session))
                .andExpect(status().isOk())
                // Não entra calada entre os avisados: "avisado" e "descoberto agora" são coisas diferentes.
                .andExpect(jsonPath("$.notifications.length()", is(1)))
                .andExpect(jsonPath("$.newDestinations.length()", is(1)))
                .andExpect(jsonPath("$.newDestinations[0].destination", is("Mercado Central")));
    }

    @Test
    @DisplayName("lote do escopo sem expedição é lacuna declarada — não se sabe onde está")
    void loteSemExpedicaoEhLacuna() throws Exception {
        var session = login();
        var scene = packagedLot(session);

        var recallId = idOf(openRecall(session, "BATCH", scene.batchId, "sem saída registrada")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        mockMvc.perform(get(RECALLS + "/" + recallId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications").isEmpty())
                .andExpect(jsonPath("$.gaps[*].expectedLink", hasItem("expedição e destino")));
    }

    @Test
    @DisplayName("a expedição não sai com mais unidades do que o lote tem")
    void expedicaoNaoPassaDoLote() throws Exception {
        var session = login();
        var scene = packagedLot(session);

        ship(session, scene.finishedLotId, "Bar do Zé", null, 5_000)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("shipment_exceeds_lot")))
                .andExpect(jsonPath("$.shipment.available").exists());
    }

    @Test
    @DisplayName("lote em quarentena não é expedido — a contenção alcança a saída")
    void loteEmQuarentenaNaoSai() throws Exception {
        var session = login();
        var scene = packagedLot(session);
        mockMvc.perform(post("/api/v1/traceability/quarantines").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"nodeType\":\"BATCH\",\"nodeId\":\"" + scene.batchId
                                + "\",\"reason\":\"investigação aberta\"}"))
                .andExpect(status().isCreated());

        ship(session, scene.finishedLotId, "Bar do Zé", null, 10)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("packaging_blocked")))
                .andExpect(jsonPath("$.blockers[*].code", hasItem("quarantined")));
    }

    @Test
    @DisplayName("abrir recall é alçada: ler não basta")
    void abrirExigeAlcada() throws Exception {
        var session = login();
        var scene = packagedLot(session);
        var leitor = principal(UUID.randomUUID(), Set.of("traceability.recall.read"));

        mockMvc.perform(post(RECALLS).with(authentication(leitor)).with(csrf())
                        .contentType("application/json")
                        .content("{\"nodeType\":\"BATCH\",\"nodeId\":\"" + scene.batchId
                                + "\",\"reason\":\"tentativa\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("recall de outra cervejaria não existe para quem pergunta")
    void isolaPorCervejaria() throws Exception {
        var session = login();
        var scene = packagedLot(session);
        var recallId = idOf(openRecall(session, "BATCH", scene.batchId, "isolamento")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        var other = principal(UUID.randomUUID(),
                Set.of("traceability.recall.read", "traceability.recall.manage"));
        mockMvc.perform(get(RECALLS).with(authentication(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(0)));
        mockMvc.perform(get(RECALLS + "/" + recallId).with(authentication(other)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("unknown_recall")));
    }

    @Test
    @DisplayName("recall sobre nó inexistente é recusado")
    void noInexistenteEhRecusado() throws Exception {
        var session = login();

        openRecall(session, "BATCH", UUID.randomUUID().toString(), "motivo")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("unknown_node")));
    }

    // --- cenário ---

    private record Scene(String batchId, String planId, String finishedLotId) {}

    /** Cadeia completa até o lote de produto acabado, com uma expedição registrada. */
    private Scene shippedLot(MockHttpSession session) throws Exception {
        var scene = packagedLot(session);
        ship(session, scene.finishedLotId, "Bar do Zé", "(11) 99999-0000", 120)
                .andExpect(status().isCreated());
        return scene;
    }

    /** Cadeia completa até o lote de produto acabado, sem nenhuma saída. */
    private Scene packagedLot(MockHttpSession session) throws Exception {
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var equipmentId = createEquipment(session);
        var maltId = createIngredient(session, "MALT", "KG", "{\"potentialSg\":\"1.037\",\"colorEbc\":\"4\"}");
        var hopId = createIngredient(session, "HOP", "G", "{\"alphaAcid\":\"12\"}");
        var yeastId = createIngredient(session, "YEAST", "UNIT", "{\"attenuation\":\"78\"}");
        receiveLot(session, maltId, 500, "KG");
        receiveLot(session, hopId, 5000, "G");
        receiveLot(session, yeastId, 50, "UNIT");

        var content = """
                {"name":"REC %s","equipmentId":"%s","batchVolumeLiters":400,"boilTimeMinutes":60,"targetIbu":30,
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
        mockMvc.perform(post("/api/v1/brew-orders/" + orderId + "/start").session(session).with(csrf()))
                .andExpect(status().isOk());

        var batchId = batchOfOrder(session, orderId);
        mockMvc.perform(post("/api/v1/production/batches/" + batchId + "/transfer").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"destinationEquipmentId\":\"" + createEquipment(session)
                                + "\",\"volumeLiters\":390,\"ogSg\":1.052,\"lossesLiters\":8}"))
                .andExpect(status().isCreated());

        var lineId = createEquipment(session);
        releaseCleaning(session, lineId);
        var containerId = createIngredient(session, "PACKAGING", "UNIT",
                "{\"volumeMl\":\"355\",\"material\":\"lata\"}");
        receiveLot(session, containerId, 2000, "UNIT");
        var planSfx = UUID.randomUUID().toString().substring(0, 8);
        var planId = idOf(mockMvc.perform(post(PLANS).session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"code":"ENV-%s","batchId":"%s","containerId":"%s","plannedUnits":400,
                                 "lineEquipmentId":"%s","plannedStart":"%s",
                                 "plannedEnd":"%s"}
                                """.formatted(planSfx, batchId, containerId, lineId, PLANNED_START, PLANNED_END)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        for (var item : new String[] {"CONTAINER_INSPECTED", "SEAL_TEST", "GAS_SUPPLY"}) {
            mockMvc.perform(post(PLANS + "/" + planId + "/checklist").session(session).with(csrf())
                            .contentType("application/json").content("{\"item\":\"" + item + "\"}"))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post(PLANS + "/" + planId + "/reserve").session(session).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post(PLANS + "/" + planId + "/execution").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"inputVolumeLiters\":145,\"producedUnits\":390,\"rejectedUnits\":5}"))
                .andExpect(status().isOk());

        return new Scene(batchId, planId, finishedLotOf(session, batchId));
    }

    // --- helpers ---

    private ResultActions openRecall(MockHttpSession session, String nodeType, String nodeId, String reason)
            throws Exception {
        return mockMvc.perform(post(RECALLS).session(session).with(csrf()).contentType("application/json")
                .content("{\"nodeType\":\"" + nodeType + "\",\"nodeId\":\"" + nodeId + "\",\"reason\":\""
                        + reason + "\"}"));
    }

    private ResultActions notify(MockHttpSession session, String recallId, String notificationId,
            String channel, String note) throws Exception {
        return mockMvc.perform(post(RECALLS + "/" + recallId + "/notifications/" + notificationId)
                .session(session).with(csrf()).contentType("application/json")
                .content("{\"channel\":\"" + channel + "\""
                        + (note == null ? "" : ",\"note\":\"" + note + "\"") + "}"));
    }

    private ResultActions close(MockHttpSession session, String recallId, String summary) throws Exception {
        return mockMvc.perform(post(RECALLS + "/" + recallId + "/close").session(session).with(csrf())
                .contentType("application/json").content("{\"summary\":\"" + summary + "\"}"));
    }

    private ResultActions ship(MockHttpSession session, String finishedLotId, String destination,
            String contact, int units) throws Exception {
        return mockMvc.perform(post(SHIPMENTS).session(session).with(csrf())
                .contentType("application/json")
                .content("{\"finishedLotId\":\"" + finishedLotId + "\",\"destination\":\"" + destination
                        + "\"" + (contact == null ? "" : ",\"contact\":\"" + contact + "\"")
                        + ",\"units\":" + units + ",\"shippedOn\":\"2026-08-21\"}"));
    }

    private String firstNotificationId(MockHttpSession session, String recallId) throws Exception {
        var body = mockMvc.perform(get(RECALLS + "/" + recallId).session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("notifications").get(0).get("id").asText();
    }

    private String finishedLotOf(MockHttpSession session, String batchId) throws Exception {
        var body = mockMvc.perform(get("/api/v1/packaging/finished-lots").session(session)
                        .param("batchId", batchId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get(0).get("id").asText();
    }

    private String batchOfOrder(MockHttpSession session, String orderId) throws Exception {
        var body = mockMvc.perform(get("/api/v1/production/batches").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (JsonNode node : JSON.readTree(body).get("content")) {
            if (node.get("orderId").asText().equals(orderId)) {
                return node.get("id").asText();
            }
        }
        throw new AssertionError("lote não encontrado para a ordem " + orderId);
    }

    private String receiveLot(MockHttpSession session, String ingredientId, int quantity, String unit)
            throws Exception {
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var supplierId = idOf(mockMvc.perform(post("/api/v1/suppliers").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"Sup " + sfx + "\",\"code\":\"S-" + sfx + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        return idOf(mockMvc.perform(post("/api/v1/inventory/lots").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"ingredientId\":\"" + ingredientId + "\",\"supplierId\":\"" + supplierId
                                + "\",\"quantity\":" + quantity + ",\"unit\":\"" + unit + "\",\"unitCost\":1.5,"
                                + "\"supplierLotCode\":\"F-" + sfx + "\","
                                + "\"expiryDate\":\"2028-01-01\",\"inspection\":\"APPROVED\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
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
                        .with(csrf())).andExpect(status().isOk());
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
        var code = type.toLowerCase(Locale.ROOT).charAt(0) + "-" + UUID.randomUUID().toString().substring(0, 8);
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
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private Authentication principal(UUID breweryId, Set<String> permissions) {
        var p = new SecurityPrincipal(UUID.randomUUID(), breweryId, "Tester", permissions);
        return new UsernamePasswordAuthenticationToken(p, "n/a", Set.of());
    }
}
