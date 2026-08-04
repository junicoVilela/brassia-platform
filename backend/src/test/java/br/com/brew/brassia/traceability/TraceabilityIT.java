package br.com.brew.brassia.traceability;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
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

/**
 * Genealogia de ponta a ponta (TRC-001), contra a cadeia real: insumo reservado → OP → lote →
 * plano de envase → execução, com as lacunas declaradas no caminho.
 */
@SpringBootTest
@Testcontainers
class TraceabilityIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String GENEALOGY = "/api/v1/traceability/genealogy";

    @Autowired WebApplicationContext context;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void doLoteParaFrenteChegaAoEnvase() throws Exception {
        var session = login();
        var scene = fullChain(session);

        mockMvc.perform(get(GENEALOGY).session(session)
                        .param("nodeType", "BATCH").param("nodeId", scene.batchId)
                        .param("direction", "FORWARD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.root.type", is("BATCH")))
                .andExpect(jsonPath("$.nodes[*].type", hasItem("PACKAGING_PLAN")))
                .andExpect(jsonPath("$.nodes[*].type", hasItem("PACKAGING_RUN")))
                .andExpect(jsonPath("$.edges[*].kind", hasItem("plano de envase")))
                .andExpect(jsonPath("$.edges[*].kind", hasItem("envase executado")))
                // Para a frente não há insumo: a reserva é ancestral, não descendente.
                .andExpect(jsonPath("$.nodes[*].type", not(hasItem("STOCK_LOT"))));
    }

    @Test
    void doLoteParaTrasChegaAoInsumoReservado() throws Exception {
        var session = login();
        var scene = fullChain(session);

        mockMvc.perform(get(GENEALOGY).session(session)
                        .param("nodeType", "BATCH").param("nodeId", scene.batchId)
                        .param("direction", "BACKWARD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes[*].type", hasItem("BREW_ORDER")))
                .andExpect(jsonPath("$.nodes[*].type", hasItem("STOCK_LOT")))
                .andExpect(jsonPath("$.edges[*].kind", hasItem("ordem executada")));
    }

    /** O elo do insumo é reserva, e a resposta precisa dizer isso — não é consumo comprovado. */
    @Test
    void aRservaChegaMarcadaComoIntencao() throws Exception {
        var session = login();
        var scene = fullChain(session);

        var body = mockMvc.perform(get(GENEALOGY).session(session)
                        .param("nodeType", "BATCH").param("nodeId", scene.batchId)
                        .param("direction", "BACKWARD"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        var reserva = edgeOfKind(body, "reserva de insumo");
        org.assertj.core.api.Assertions.assertThat(reserva.get("strength").asText()).isEqualTo("INTENDED");
        var ordem = edgeOfKind(body, "ordem executada");
        org.assertj.core.api.Assertions.assertThat(ordem.get("strength").asText()).isEqualTo("CONFIRMED");
    }

    @Test
    void asTresLacunasSaoEvidenciadas() throws Exception {
        var session = login();
        var scene = fullChain(session);

        mockMvc.perform(get(GENEALOGY).session(session)
                        .param("nodeType", "BATCH").param("nodeId", scene.batchId)
                        .param("direction", "BOTH"))
                .andExpect(status().isOk())
                // Blend (TRC-001-A), produto acabado (TRC-001-B) e consumo no dia de brassa (TRC-001-C).
                .andExpect(jsonPath("$.gaps[*].expectedLink", hasItem("blend de lotes")))
                .andExpect(jsonPath("$.gaps[*].expectedLink", hasItem("lote de produto acabado e destino")))
                .andExpect(jsonPath("$.gaps[*].expectedLink", hasItem("consumo de insumo por lote")));
    }

    @Test
    void oCorteDeProfundidadeAvisaQueHaMais() throws Exception {
        var session = login();
        var scene = fullChain(session);

        mockMvc.perform(get(GENEALOGY).session(session)
                        .param("nodeType", "BATCH").param("nodeId", scene.batchId)
                        .param("direction", "FORWARD").param("depth", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.truncated", is(true)))
                .andExpect(jsonPath("$.nodes[*].type", not(hasItem("PACKAGING_RUN"))));
    }

    @Test
    void profundidadeAcimaDoTetoEhRecusada() throws Exception {
        var session = login();
        var scene = fullChain(session);

        mockMvc.perform(get(GENEALOGY).session(session)
                        .param("nodeType", "BATCH").param("nodeId", scene.batchId).param("depth", "50"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("depth_exceeded")))
                .andExpect(jsonPath("$.depth.maximum", is(10)));
    }

    @Test
    void noInexistenteEhRecusadoEmVezDeDevolverGrafoVazio() throws Exception {
        var session = login();

        mockMvc.perform(get(GENEALOGY).session(session)
                        .param("nodeType", "BATCH").param("nodeId", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("unknown_node")));
    }

    @Test
    void semPermissaoNaoConsulta() throws Exception {
        var session = login();
        var scene = fullChain(session);

        mockMvc.perform(get(GENEALOGY).with(authentication(principal(UUID.randomUUID(), Set.of())))
                        .param("nodeType", "BATCH").param("nodeId", scene.batchId))
                .andExpect(status().isForbidden());
    }

    @Test
    void naoEnxergaLoteDeOutraCervejaria() throws Exception {
        var session = login();
        var scene = fullChain(session);

        // Mesma permissão, cervejaria diferente: o nó simplesmente não existe para quem pergunta.
        mockMvc.perform(get(GENEALOGY)
                        .with(authentication(principal(UUID.randomUUID(),
                                Set.of("traceability.genealogy.read"))))
                        .param("nodeType", "BATCH").param("nodeId", scene.batchId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("unknown_node")));
    }

    @Test
    void aConsultaRepetidaDevolveOMesmoGrafo() throws Exception {
        var session = login();
        var scene = fullChain(session);

        var first = genealogyBody(session, scene.batchId);
        var second = genealogyBody(session, scene.batchId);

        // Consulta é derivada e ordenada de forma estável: repetir não pode mudar o resultado.
        org.assertj.core.api.Assertions.assertThat(first).isEqualTo(second);
    }

    @Test
    void doInsumoParaFrenteAlcancaOLote() throws Exception {
        var session = login();
        var scene = fullChain(session);

        mockMvc.perform(get(GENEALOGY).session(session)
                        .param("nodeType", "STOCK_LOT").param("nodeId", scene.maltLotId)
                        .param("direction", "FORWARD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes[*].type", hasItem("BREW_ORDER")))
                .andExpect(jsonPath("$.nodes[*].type", hasItem("BATCH")));
    }

    // --- cenário ---

    private record Scene(String orderId, String batchId, String planId, String maltLotId) {}

    /** Insumo recebido e reservado → OP → lote → plano de envase reservado → execução. */
    private Scene fullChain(MockHttpSession session) throws Exception {
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var equipmentId = createEquipment(session);
        var maltId = createIngredient(session, "MALT", "KG", "{\"potentialSg\":\"1.037\",\"colorEbc\":\"4\"}");
        var hopId = createIngredient(session, "HOP", "G", "{\"alphaAcid\":\"12\"}");
        var yeastId = createIngredient(session, "YEAST", "UNIT", "{\"attenuation\":\"78\"}");
        var maltLotId = receiveLot(session, maltId, 500, "KG");
        receiveLot(session, hopId, 5000, "G");
        receiveLot(session, yeastId, 50, "UNIT");

        var recipeId = publishedRecipe(session, sfx, equipmentId, maltId, hopId, yeastId);
        var orderId = idOf(mockMvc.perform(post("/api/v1/brew-orders").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"recipeId\":\"" + recipeId + "\",\"volumeLiters\":400}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/v1/brew-orders/" + orderId + "/release").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"assignedUserId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk());
        // É a reserva que cria o elo insumo → OP.
        mockMvc.perform(post("/api/v1/brew-orders/" + orderId + "/reserve-stock").session(session).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/brew-orders/" + orderId + "/start").session(session).with(csrf()))
                .andExpect(status().isOk());

        var batchId = batchOfOrder(session, orderId);
        transfer(session, batchId, createEquipment(session));
        var planId = executedPlan(session, batchId);
        return new Scene(orderId, batchId, planId, maltLotId);
    }

    private String executedPlan(MockHttpSession session, String batchId) throws Exception {
        var containerId = createIngredient(session, "PACKAGING", "UNIT",
                "{\"volumeMl\":\"355\",\"material\":\"lata\"}");
        receiveLot(session, containerId, 2000, "UNIT");
        var lineId = createEquipment(session);
        releaseCleaning(session, lineId);
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var planId = idOf(mockMvc.perform(post("/api/v1/packaging/plans").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"code":"ENV-%s","batchId":"%s","containerId":"%s","plannedUnits":800,
                                 "lineEquipmentId":"%s","plannedStart":"2026-08-20T09:00:00Z",
                                 "plannedEnd":"2026-08-20T15:00:00Z"}
                                """.formatted(sfx, batchId, containerId, lineId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        for (var item : new String[] {"CONTAINER_INSPECTED", "SEAL_TEST", "GAS_SUPPLY"}) {
            mockMvc.perform(post("/api/v1/packaging/plans/" + planId + "/checklist").session(session).with(csrf())
                            .contentType("application/json").content("{\"item\":\"" + item + "\"}"))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/v1/packaging/plans/" + planId + "/reserve").session(session).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/packaging/plans/" + planId + "/execution").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"inputVolumeLiters\":284,\"producedUnits\":780,\"rejectedUnits\":12}"))
                .andExpect(status().isOk());
        return planId;
    }

    // --- helpers ---

    private String genealogyBody(MockHttpSession session, String batchId) throws Exception {
        return mockMvc.perform(get(GENEALOGY).session(session)
                        .param("nodeType", "BATCH").param("nodeId", batchId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private static JsonNode edgeOfKind(String body, String kind) throws Exception {
        for (JsonNode edge : JSON.readTree(body).get("edges")) {
            if (edge.get("kind").asText().equals(kind)) {
                return edge;
            }
        }
        throw new AssertionError("aresta ausente: " + kind);
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

    private void transfer(MockHttpSession session, String batchId, String fermenterId) throws Exception {
        mockMvc.perform(post("/api/v1/production/batches/" + batchId + "/transfer").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"destinationEquipmentId\":\"" + fermenterId + "\",\"volumeLiters\":390,"
                                + "\"ogSg\":1.052,\"lossesLiters\":8}"))
                .andExpect(status().isCreated());
    }

    private String publishedRecipe(MockHttpSession session, String sfx, String equipmentId, String maltId,
            String hopId, String yeastId) throws Exception {
        var content = """
                {"name":"Trace %s","equipmentId":"%s","batchVolumeLiters":400,"boilTimeMinutes":60,
                 "targetIbu":30,
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
        return recipeId;
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
