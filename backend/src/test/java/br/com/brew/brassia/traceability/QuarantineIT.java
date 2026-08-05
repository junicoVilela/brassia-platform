package br.com.brew.brassia.traceability;

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
import java.util.Locale;
import java.util.Set;
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
 * Quarentena de ponta a ponta (FDS-002), contra a cadeia real.
 *
 * <p>O que estes testes fixam é a propagação: quarentenar o lote impede o envase que nasce dele,
 * sem que ninguém bloqueie o envase; e quarentenar o insumo alcança o mesmo envase <em>por
 * suspeita</em>, porque o elo do insumo é reserva e não consumo comprovado.
 */
@SpringBootTest
@Testcontainers
class QuarantineIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String QUARANTINES = "/api/v1/traceability/quarantines";
    private static final String PLANS = "/api/v1/packaging/plans";

    @Autowired WebApplicationContext context;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("quarentenar o lote impede o envase que nasce dele")
    void quarentenaDoLoteImpedeOEnvase() throws Exception {
        var session = login();
        var scene = reservedPlan(session);

        openQuarantine(session, "BATCH", scene.batchId, "desvio de pH na fermentação")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("OPEN")));

        // Ninguém bloqueou o plano: o bloqueio é herdado do lote pelo grafo.
        execute(session, scene.planId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("packaging_blocked")))
                .andExpect(jsonPath("$.blockers[*].code", hasItem("quarantined")));
    }

    @Test
    @DisplayName("um plano criado depois da abertura também nasce bloqueado")
    void planoCriadoDepoisNasceBloqueado() throws Exception {
        var session = login();
        var scene = reservedPlan(session);
        openQuarantine(session, "BATCH", scene.batchId, "investigação sensorial")
                .andExpect(status().isCreated());

        // O alcance é derivado a cada pergunta; uma lista congelada na abertura deixaria este passar.
        var outroPlano = plannedPlan(session, scene.batchId, cleanLine(session));
        mockMvc.perform(post(PLANS + "/" + outroPlano + "/reserve").session(session).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.blockers[*].code", hasItem("quarantined")));
    }

    @Test
    @DisplayName("quarentenar o insumo alcança o envase por suspeita — a reserva é intenção")
    void quarentenaDoInsumoAlcancaPorSuspeita() throws Exception {
        var session = login();
        var scene = reservedPlan(session);

        openQuarantine(session, "STOCK_LOT", scene.maltLotId, "laudo do fornecedor sob análise")
                .andExpect(status().isCreated());

        // Bloqueia igual, e diz que é suspeita: quem investiga precisa saber onde apertar primeiro.
        execute(session, scene.planId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.blockers[*].code", hasItem("quarantine_suspected")));
    }

    @Test
    @DisplayName("liberar devolve o envase ao normal")
    void liberarDesbloqueia() throws Exception {
        var session = login();
        var scene = reservedPlan(session);
        var quarantineId = idOf(openQuarantine(session, "BATCH", scene.batchId, "suspeita de contaminação")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        execute(session, scene.planId).andExpect(status().isConflict());

        release(session, quarantineId, "contraprova microbiológica negativa").andExpect(status().isOk());

        execute(session, scene.planId).andExpect(status().isOk());
    }

    @Test
    @DisplayName("o detalhe mostra o alcance de hoje, com a força de cada elo")
    void detalheMostraOAlcance() throws Exception {
        var session = login();
        var scene = reservedPlan(session);
        var quarantineId = idOf(openQuarantine(session, "STOCK_LOT", scene.maltLotId, "laudo sob análise")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        mockMvc.perform(get(QUARANTINES + "/" + quarantineId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quarantine.origin.type", is("STOCK_LOT")))
                .andExpect(jsonPath("$.affected[*].node.type", hasItem("BREW_ORDER")))
                .andExpect(jsonPath("$.affected[*].node.type", hasItem("PACKAGING_PLAN")))
                // Tudo que vem depois da reserva é suspeita, e a resposta diz isso nó a nó.
                .andExpect(jsonPath("$.affected[?(@.node.type=='PACKAGING_PLAN')].suspected",
                        hasItem(true)));
    }

    @Test
    @DisplayName("abrir a segunda quarentena do mesmo nó é recusado")
    void abrirDuasVezesEhRecusado() throws Exception {
        var session = login();
        var scene = reservedPlan(session);
        openQuarantine(session, "BATCH", scene.batchId, "primeira").andExpect(status().isCreated());

        openQuarantine(session, "BATCH", scene.batchId, "segunda")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("already_quarantined")))
                .andExpect(jsonPath("$.quarantineId").exists());
    }

    @Test
    @DisplayName("quarentenar nó inexistente é recusado em vez de criar bloqueio de nada")
    void noInexistenteEhRecusado() throws Exception {
        var session = login();

        openQuarantine(session, "BATCH", UUID.randomUUID().toString(), "motivo")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("unknown_node")));
    }

    @Test
    @DisplayName("liberar é alçada própria: abrir não basta")
    void liberarExigeAlcadaPropria() throws Exception {
        var session = login();
        var scene = reservedPlan(session);
        var quarantineId = idOf(openQuarantine(session, "BATCH", scene.batchId, "suspeita")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        var abridor = principal(UUID.randomUUID(),
                Set.of("traceability.quarantine.read", "traceability.quarantine.open"));
        mockMvc.perform(post(QUARANTINES + "/" + quarantineId + "/release")
                        .with(authentication(abridor)).with(csrf()).contentType("application/json")
                        .content("{\"justification\":\"deixa eu liberar\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("liberar sem justificativa é recusado")
    void liberarSemJustificativaEhRecusado() throws Exception {
        var session = login();
        var scene = reservedPlan(session);
        var quarantineId = idOf(openQuarantine(session, "BATCH", scene.batchId, "suspeita")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        release(session, quarantineId, "   ").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("quarentena de outra cervejaria não existe para quem pergunta")
    void isolaPorCervejaria() throws Exception {
        var session = login();
        var scene = reservedPlan(session);
        var quarantineId = idOf(openQuarantine(session, "BATCH", scene.batchId, "suspeita")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        var other = principal(UUID.randomUUID(), Set.of("traceability.quarantine.read",
                "traceability.quarantine.open", "traceability.quarantine.release"));
        mockMvc.perform(get(QUARANTINES).with(authentication(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(0)));
        mockMvc.perform(get(QUARANTINES + "/" + quarantineId).with(authentication(other)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("unknown_quarantine")));
    }

    @Test
    @DisplayName("sem permissão nenhuma não se lê nem se abre")
    void semPermissaoNaoOpera() throws Exception {
        var session = login();
        var scene = reservedPlan(session);
        var cego = principal(UUID.randomUUID(), Set.of());

        mockMvc.perform(get(QUARANTINES).with(authentication(cego)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(QUARANTINES).with(authentication(cego)).with(csrf())
                        .contentType("application/json")
                        .content("{\"nodeType\":\"BATCH\",\"nodeId\":\"" + scene.batchId
                                + "\",\"reason\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    // --- cenário ---

    private record Scene(String batchId, String planId, String maltLotId) {}

    /** Insumo reservado → OP → lote → plano de envase reservado, pronto para executar. */
    private Scene reservedPlan(MockHttpSession session) throws Exception {
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var equipmentId = createEquipment(session);
        var maltId = createIngredient(session, "MALT", "KG", "{\"potentialSg\":\"1.037\",\"colorEbc\":\"4\"}");
        var hopId = createIngredient(session, "HOP", "G", "{\"alphaAcid\":\"12\"}");
        var yeastId = createIngredient(session, "YEAST", "UNIT", "{\"attenuation\":\"78\"}");
        var maltLotId = receiveLot(session, maltId, 500, "KG");
        receiveLot(session, hopId, 5000, "G");
        receiveLot(session, yeastId, 50, "UNIT");

        var content = """
                {"name":"QRT %s","equipmentId":"%s","batchVolumeLiters":400,"boilTimeMinutes":60,"targetIbu":30,
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
        // É a reserva que cria o elo insumo → OP, e ele é intenção, não consumo.
        mockMvc.perform(post("/api/v1/brew-orders/" + orderId + "/reserve-stock").session(session).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/brew-orders/" + orderId + "/start").session(session).with(csrf()))
                .andExpect(status().isOk());

        var batchId = batchOfOrder(session, orderId);
        mockMvc.perform(post("/api/v1/production/batches/" + batchId + "/transfer").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"destinationEquipmentId\":\"" + createEquipment(session)
                                + "\",\"volumeLiters\":390,\"ogSg\":1.052,\"lossesLiters\":8}"))
                .andExpect(status().isCreated());

        var planId = plannedPlan(session, batchId, cleanLine(session));
        mockMvc.perform(post(PLANS + "/" + planId + "/reserve").session(session).with(csrf()))
                .andExpect(status().isOk());
        return new Scene(batchId, planId, maltLotId);
    }

    private String plannedPlan(MockHttpSession session, String batchId, String lineId) throws Exception {
        var containerId = createIngredient(session, "PACKAGING", "UNIT",
                "{\"volumeMl\":\"355\",\"material\":\"lata\"}");
        receiveLot(session, containerId, 2000, "UNIT");
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var planId = idOf(mockMvc.perform(post(PLANS).session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"code":"ENV-%s","batchId":"%s","containerId":"%s","plannedUnits":400,
                                 "lineEquipmentId":"%s","plannedStart":"2026-08-20T09:00:00Z",
                                 "plannedEnd":"2026-08-20T15:00:00Z"}
                                """.formatted(sfx, batchId, containerId, lineId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        for (var item : new String[] {"CONTAINER_INSPECTED", "SEAL_TEST", "GAS_SUPPLY"}) {
            mockMvc.perform(post(PLANS + "/" + planId + "/checklist").session(session).with(csrf())
                            .contentType("application/json").content("{\"item\":\"" + item + "\"}"))
                    .andExpect(status().isOk());
        }
        return planId;
    }

    private String cleanLine(MockHttpSession session) throws Exception {
        var lineId = createEquipment(session);
        releaseCleaning(session, lineId);
        return lineId;
    }

    // --- helpers ---

    private ResultActions openQuarantine(MockHttpSession session, String nodeType, String nodeId, String reason)
            throws Exception {
        return mockMvc.perform(post(QUARANTINES).session(session).with(csrf())
                .contentType("application/json")
                .content("{\"nodeType\":\"" + nodeType + "\",\"nodeId\":\"" + nodeId + "\",\"reason\":\""
                        + reason + "\"}"));
    }

    private ResultActions release(MockHttpSession session, String quarantineId, String justification)
            throws Exception {
        return mockMvc.perform(post(QUARANTINES + "/" + quarantineId + "/release").session(session).with(csrf())
                .contentType("application/json")
                .content("{\"justification\":\"" + justification + "\"}"));
    }

    private ResultActions execute(MockHttpSession session, String planId) throws Exception {
        return mockMvc.perform(post(PLANS + "/" + planId + "/execution").session(session).with(csrf())
                .contentType("application/json")
                .content("{\"inputVolumeLiters\":145,\"producedUnits\":390,\"rejectedUnits\":5}"));
    }

    private String batchOfOrder(MockHttpSession session, String orderId) throws Exception {
        var body = mockMvc.perform(get("/api/v1/production/batches").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (JsonNode node : JSON.readTree(body)) {
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
