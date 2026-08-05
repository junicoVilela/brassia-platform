package br.com.brew.brassia.production;

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
 * Consumo do dia de brassa (TRC-001-C).
 *
 * <p>O que estes testes fixam é a passagem de intenção a fato: antes do registro, o elo do insumo
 * na genealogia é a reserva e vem marcado {@code INTENDED}, com a lacuna declarada; depois, é o
 * consumo confirmado, {@code CONFIRMED}, e a lacuna some. É o critério de remoção do débito,
 * verificado contra a cadeia real.
 */
@SpringBootTest
@Testcontainers
class BrewConsumptionIT {

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
    @DisplayName("a proposta é a reserva da OP, lote a lote")
    void propostaEhAReserva() throws Exception {
        var session = login();
        var scene = startedBatch(session);

        mockMvc.perform(get("/api/v1/production/batches/" + scene.batchId + "/consumption/proposal")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyRegistered", is(false)))
                .andExpect(jsonPath("$.reserved.length()", is(3)))
                .andExpect(jsonPath("$.reserved[*].supplierLotCode").isNotEmpty())
                .andExpect(jsonPath("$.reserved[*].reserved").isNotEmpty());
    }

    @Test
    @DisplayName("confirmar o consumo troca a intenção pelo fato na genealogia")
    void consumoTrocaIntencaoPorFato() throws Exception {
        var session = login();
        var scene = startedBatch(session);

        // Antes: o elo é reserva, é intenção, e a lacuna está declarada.
        var antes = genealogy(session, scene.batchId);
        assertEdge(antes, "reserva de insumo", "INTENDED");
        org.assertj.core.api.Assertions.assertThat(antes)
                .contains("consumo de insumo por lote");

        registerFromProposal(session, scene.batchId).andExpect(status().isOk());

        // Depois: o elo é consumo, é fato, e a lacuna sumiu.
        var depois = genealogy(session, scene.batchId);
        assertEdge(depois, "consumo de insumo", "CONFIRMED");
        mockMvc.perform(get(GENEALOGY).session(session)
                        .param("nodeType", "BATCH").param("nodeId", scene.batchId)
                        .param("direction", "BACKWARD"))
                .andExpect(jsonPath("$.gaps[*].expectedLink", not(hasItem("consumo de insumo por lote"))))
                // A reserva não aparece junto: contaria o mesmo malte duas vezes.
                .andExpect(jsonPath("$.edges[*].kind", not(hasItem("reserva de insumo"))));
    }

    @Test
    @DisplayName("o consumo baixa o estoque de verdade, e libera o que sobrou da reserva")
    void consumoBaixaOEstoque() throws Exception {
        var session = login();
        var scene = startedBatch(session);
        var antes = lotBalance(session, scene.maltLotId);

        registerFromProposal(session, scene.batchId).andExpect(status().isOk());

        var depois = lotBalance(session, scene.maltLotId);
        org.assertj.core.api.Assertions.assertThat(depois.get("onHand").decimalValue())
                .isLessThan(antes.get("onHand").decimalValue());
        // Reserva zerada: a OP brassada não fica segurando insumo que já virou cerveja.
        org.assertj.core.api.Assertions.assertThat(depois.get("reserved").decimalValue().signum())
                .isZero();
    }

    @Test
    @DisplayName("registrar duas vezes é recusado: dobraria o consumo e o custo")
    void naoRegistraDuasVezes() throws Exception {
        var session = login();
        var scene = startedBatch(session);
        registerFromProposal(session, scene.batchId).andExpect(status().isOk());

        // A proposta esvazia depois do registro — a reserva virou consumo —, então a tela nem
        // consegue reenviar. A guarda existe para quem chama a API direto.
        mockMvc.perform(get("/api/v1/production/batches/" + scene.batchId + "/consumption/proposal")
                        .session(session))
                .andExpect(jsonPath("$.alreadyRegistered", is(true)))
                .andExpect(jsonPath("$.reserved").isEmpty());
        register(session, scene.batchId,
                "[{\"lotId\":\"" + scene.maltLotId + "\",\"quantity\":1,\"unit\":\"KG\"}]")
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("declarar mais do que o lote tem é recusado, com a falta lote a lote")
    void naoConsomeMaisDoQueExiste() throws Exception {
        var session = login();
        var scene = startedBatch(session);

        register(session, scene.batchId,
                "[{\"lotId\":\"" + scene.maltLotId + "\",\"quantity\":9999,\"unit\":\"KG\"}]")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("brew_consumption_shortfall")))
                .andExpect(jsonPath("$.shortfalls[0].available").exists());
    }

    @Test
    @DisplayName("consumo sem linha nenhuma não é consumo")
    void listaVaziaEhRecusada() throws Exception {
        var session = login();
        var scene = startedBatch(session);

        register(session, scene.batchId, "[]").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("registrar consumo é alçada de quem opera o lote")
    void semPermissaoNaoRegistra() throws Exception {
        var session = login();
        var scene = startedBatch(session);
        var leitor = principal(UUID.randomUUID(), Set.of("production.batch.read"));

        mockMvc.perform(post("/api/v1/production/batches/" + scene.batchId + "/consumption")
                        .with(authentication(leitor)).with(csrf()).contentType("application/json")
                        .content("{\"lines\":[{\"lotId\":\"" + scene.maltLotId
                                + "\",\"quantity\":1,\"unit\":\"KG\"}]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("lote de outra cervejaria não existe para quem pergunta")
    void isolaPorCervejaria() throws Exception {
        var session = login();
        var scene = startedBatch(session);
        var other = principal(UUID.randomUUID(),
                Set.of("production.batch.read", "production.batch.manage"));

        mockMvc.perform(get("/api/v1/production/batches/" + scene.batchId + "/consumption/proposal")
                        .with(authentication(other)))
                .andExpect(status().isBadRequest());
    }

    // --- cenário ---

    private record Scene(String batchId, String maltLotId) {}

    /** OP liberada, com estoque reservado e brassagem iniciada — o estado em que se registra consumo. */
    private Scene startedBatch(MockHttpSession session) throws Exception {
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var equipmentId = createEquipment(session);
        var maltId = createIngredient(session, "MALT", "KG", "{\"potentialSg\":\"1.037\",\"colorEbc\":\"4\"}");
        var hopId = createIngredient(session, "HOP", "G", "{\"alphaAcid\":\"12\"}");
        var yeastId = createIngredient(session, "YEAST", "UNIT", "{\"attenuation\":\"78\"}");
        var maltLotId = receiveLot(session, maltId, 500, "KG");
        receiveLot(session, hopId, 5000, "G");
        receiveLot(session, yeastId, 50, "UNIT");

        var content = """
                {"name":"Consumo %s","equipmentId":"%s","batchVolumeLiters":400,"boilTimeMinutes":60,
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

        var orderId = idOf(mockMvc.perform(post("/api/v1/brew-orders").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"recipeId\":\"" + recipeId + "\",\"volumeLiters\":400}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/v1/brew-orders/" + orderId + "/release").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"assignedUserId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/brew-orders/" + orderId + "/reserve-stock").session(session).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/brew-orders/" + orderId + "/start").session(session).with(csrf()))
                .andExpect(status().isOk());

        return new Scene(batchOfOrder(session, orderId), maltLotId);
    }

    // --- helpers ---

    /** Confirma a proposta como veio — o caso comum: a brassagem usou o que a OP separou. */
    private ResultActions registerFromProposal(MockHttpSession session, String batchId) throws Exception {
        var body = mockMvc.perform(get("/api/v1/production/batches/" + batchId + "/consumption/proposal")
                        .session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var lines = new StringBuilder("[");
        for (JsonNode lot : JSON.readTree(body).get("reserved")) {
            if (lines.length() > 1) {
                lines.append(',');
            }
            lines.append("{\"lotId\":\"").append(lot.get("lotId").asText())
                    .append("\",\"quantity\":").append(lot.get("reserved").asText())
                    .append(",\"unit\":\"").append(lot.get("unit").asText()).append("\"}");
        }
        return register(session, batchId, lines.append(']').toString());
    }

    private ResultActions register(MockHttpSession session, String batchId, String lines) throws Exception {
        return mockMvc.perform(post("/api/v1/production/batches/" + batchId + "/consumption")
                .session(session).with(csrf()).contentType("application/json")
                .content("{\"lines\":" + lines + "}"));
    }

    private String genealogy(MockHttpSession session, String batchId) throws Exception {
        return mockMvc.perform(get(GENEALOGY).session(session)
                        .param("nodeType", "BATCH").param("nodeId", batchId)
                        .param("direction", "BACKWARD"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private static void assertEdge(String body, String kind, String strength) throws Exception {
        for (JsonNode edge : JSON.readTree(body).get("edges")) {
            if (edge.get("kind").asText().equals(kind)) {
                org.assertj.core.api.Assertions.assertThat(edge.get("strength").asText())
                        .isEqualTo(strength);
                return;
            }
        }
        throw new AssertionError("aresta ausente: " + kind);
    }

    private JsonNode lotBalance(MockHttpSession session, String lotId) throws Exception {
        var body = mockMvc.perform(get("/api/v1/inventory/lots/" + lotId + "/balance").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body);
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

    private String createEquipment(MockHttpSession session) throws Exception {
        var code = "EQ-" + UUID.randomUUID().toString().substring(0, 8);
        return idOf(mockMvc.perform(post("/api/v1/equipment").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"" + code + "\",\"name\":\"Panela\",\"capacityLiters\":500,"
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
