package br.com.brew.brassia.traceability;

import static org.hamcrest.Matchers.containsString;
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
 * Simulado de recall de ponta a ponta (FDS-004).
 *
 * <p>O teste que mais importa aqui é o que <strong>não</strong> acontece: rodar um simulado inteiro
 * não pode criar expedição, recall, quarentena nem pendência de comunicação. "Sem afetar estoque
 * real" é a restrição da história, e é a que um refactor futuro tem mais chance de quebrar em
 * silêncio.
 */
@SpringBootTest
@Testcontainers
class RecallDrillIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String DRILLS = "/api/v1/traceability/recall-drills";
    private static final String SHIPMENTS = "/api/v1/packaging/shipments";
    private static final String PLANS = "/api/v1/packaging/plans";

    @Autowired WebApplicationContext context;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("o simulado mede cobertura, tempo e lacunas — e não muda nada")
    void mediaSemAfetarEstoque() throws Exception {
        var session = login();
        var scene = shippedLot(session);

        var drillId = idOf(start(session, scene.batchId, "exercício trimestral")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code", startsWith("SIM-")))
                .andExpect(jsonPath("$.status", is("RUNNING")))
                .andReturn().getResponse().getContentAsString());

        // Enquanto corre, o relatório mostra o alvo: o que saiu e para onde.
        mockMvc.perform(get(DRILLS + "/" + drillId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unitsInScope", is(120)))
                .andExpect(jsonPath("$.destinationsReached", is(1)))
                .andExpect(jsonPath("$.destinations[0].destination", is("Bar do Zé")));

        finish(session, drillId, 90, "duas ligações; 30 latas não localizadas", "revisar contatos")
                .andExpect(status().isOk());

        mockMvc.perform(get(DRILLS + "/" + drillId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.drill.status", is("FINISHED")))
                .andExpect(jsonPath("$.drill.unitsLocated", is(90)))
                .andExpect(jsonPath("$.drill.locatedPercent", is(75)))
                .andExpect(jsonPath("$.drill.correctiveActions", is("revisar contatos")));

        // O que o simulado não fez: nenhuma expedição nova, nenhum recall, nenhuma quarentena.
        mockMvc.perform(get(SHIPMENTS).session(session))
                .andExpect(jsonPath("$.length()", is(1)));
        mockMvc.perform(get("/api/v1/traceability/recalls").session(session))
                .andExpect(jsonPath("$.length()", is(0)));
        mockMvc.perform(get("/api/v1/traceability/quarantines").session(session))
                .andExpect(jsonPath("$.length()", is(0)));
    }

    @Test
    @DisplayName("as lacunas viram ação corretiva sugerida no relatório")
    void lacunasViramAcaoCorretiva() throws Exception {
        var session = login();
        var scene = packagedLot(session);

        var drillId = idOf(start(session, scene.batchId, null)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        mockMvc.perform(get(DRILLS + "/" + drillId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gaps[*].expectedLink", hasItem("expedição e destino")))
                // O relatório não diz só que faltou: diz o que fazer para não faltar de novo.
                .andExpect(jsonPath("$.findings[0]", containsString("sem expedição registrada")));
    }

    @Test
    @DisplayName("destino sem contato é achado do exercício, não detalhe")
    void destinoSemContatoEhAchado() throws Exception {
        var session = login();
        var scene = packagedLot(session);
        ship(session, scene.finishedLotId, "Mercado sem contato", null, 40)
                .andExpect(status().isCreated());

        var drillId = idOf(start(session, scene.batchId, null)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        mockMvc.perform(get(DRILLS + "/" + drillId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findings", hasItem(containsString("sem contato cadastrado"))));
    }

    @Test
    @DisplayName("localizar mais do que saiu é recusado")
    void naoLocalizaMaisDoQueSaiu() throws Exception {
        var session = login();
        var scene = shippedLot(session);
        var drillId = idOf(start(session, scene.batchId, null)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        finish(session, drillId, 5000, "achei tudo e mais um pouco", null)
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("simulado encerrado não é encerrado de novo")
    void naoEncerraDuasVezes() throws Exception {
        var session = login();
        var scene = shippedLot(session);
        var drillId = idOf(start(session, scene.batchId, null)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        finish(session, drillId, 120, "tudo localizado", null).andExpect(status().isOk());
        finish(session, drillId, 120, "de novo", null).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("simulado sobre nó inexistente é recusado")
    void noInexistenteEhRecusado() throws Exception {
        var session = login();

        start(session, UUID.randomUUID().toString(), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("unknown_node")));
    }

    @Test
    @DisplayName("simulado de outra cervejaria não existe para quem pergunta")
    void isolaPorCervejaria() throws Exception {
        var session = login();
        var scene = shippedLot(session);
        var drillId = idOf(start(session, scene.batchId, null)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        var other = principal(UUID.randomUUID(),
                Set.of("traceability.drill.read", "traceability.drill.manage"));
        mockMvc.perform(get(DRILLS).with(authentication(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(0)));
        mockMvc.perform(get(DRILLS + "/" + drillId).with(authentication(other)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("unknown_drill")));
    }

    @Test
    @DisplayName("ler o relatório não basta para rodar o exercício")
    void semPermissaoNaoRoda() throws Exception {
        var session = login();
        var scene = shippedLot(session);
        var leitor = principal(UUID.randomUUID(), Set.of("traceability.drill.read"));

        mockMvc.perform(post(DRILLS).with(authentication(leitor)).with(csrf())
                        .contentType("application/json")
                        .content("{\"nodeType\":\"BATCH\",\"nodeId\":\"" + scene.batchId + "\"}"))
                .andExpect(status().isForbidden());
    }

    // --- cenário ---

    private record Scene(String batchId, String finishedLotId) {}

    private Scene shippedLot(MockHttpSession session) throws Exception {
        var scene = packagedLot(session);
        ship(session, scene.finishedLotId, "Bar do Zé", "(11) 99999-0000", 120)
                .andExpect(status().isCreated());
        return scene;
    }

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
                {"name":"SIM %s","equipmentId":"%s","batchVolumeLiters":400,"boilTimeMinutes":60,"targetIbu":30,
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
                                 "lineEquipmentId":"%s","plannedStart":"2026-08-20T09:00:00Z",
                                 "plannedEnd":"2026-08-20T15:00:00Z"}
                                """.formatted(planSfx, batchId, containerId, lineId)))
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

        return new Scene(batchId, finishedLotOf(session, batchId));
    }

    // --- helpers ---

    private ResultActions start(MockHttpSession session, String nodeId, String note) throws Exception {
        return mockMvc.perform(post(DRILLS).session(session).with(csrf()).contentType("application/json")
                .content("{\"nodeType\":\"BATCH\",\"nodeId\":\"" + nodeId + "\""
                        + (note == null ? "" : ",\"note\":\"" + note + "\"") + "}"));
    }

    private ResultActions finish(MockHttpSession session, String drillId, int located, String summary,
            String actions) throws Exception {
        return mockMvc.perform(post(DRILLS + "/" + drillId + "/finish").session(session).with(csrf())
                .contentType("application/json")
                .content("{\"unitsLocated\":" + located + ",\"summary\":\"" + summary + "\""
                        + (actions == null ? "" : ",\"correctiveActions\":\"" + actions + "\"") + "}"));
    }

    private ResultActions ship(MockHttpSession session, String finishedLotId, String destination,
            String contact, int units) throws Exception {
        return mockMvc.perform(post(SHIPMENTS).session(session).with(csrf())
                .contentType("application/json")
                .content("{\"finishedLotId\":\"" + finishedLotId + "\",\"destination\":\"" + destination
                        + "\"" + (contact == null ? "" : ",\"contact\":\"" + contact + "\"")
                        + ",\"units\":" + units + ",\"shippedOn\":\"2026-08-21\"}"));
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
