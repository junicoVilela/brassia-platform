package br.com.brew.brassia.production;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.junit.jupiter.api.DisplayName;
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

@SpringBootTest
@Testcontainers
class MeasurementIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired WebApplicationContext context;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void recordsMeasurementAndListsIt() throws Exception {
        var session = login();
        var batch = startedBatch(session);
        var batchId = batch.get("id").asText();
        var stepId = batch.get("steps").get(0).get("id").asText();

        mockMvc.perform(post("/api/v1/production/batches/" + batchId + "/measurements").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"stepId\":\"" + stepId + "\",\"kind\":\"DENSITY\",\"value\":1.048,\"unit\":\"sg\","
                                + "\"temperatureC\":18.5,\"method\":\"densímetro\",\"source\":\"MANUAL\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/production/batches/" + batchId + "/measurements").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].kind", is("DENSITY")))
                .andExpect(jsonPath("$[0].unit", is("SG")))
                .andExpect(jsonPath("$[0].value", is(1.048)))
                .andExpect(jsonPath("$[*].stepId", hasItem(stepId)));
    }

    @Test
    @DisplayName("PWA-002: o mesmo apontamento reenviado responde 200 e não cria segunda medição")
    void offlineQueueRetryIsIdempotent() throws Exception {
        // A fila do aparelho reenvia até receber confirmação — é assim que ela não perde o apontamento de
        // quem estava sem rede. "Ao menos uma vez" só vira "exatamente uma vez" porque este lado reconhece
        // a repetição pela chave gerada NO REGISTRO, não no envio.
        var session = login();
        var batchId = startedBatch(session).get("id").asText();
        var clientRequestId = "apontamento-" + UUID.randomUUID();

        var body = "{\"kind\":\"TEMPERATURE\",\"value\":66,\"unit\":\"C\",\"source\":\"MANUAL\","
                + "\"clientRequestId\":\"" + clientRequestId + "\"}";

        var primeira = mockMvc.perform(post("/api/v1/production/batches/" + batchId + "/measurements")
                        .session(session).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.duplicate", is(false)))
                .andReturn().getResponse().getContentAsString();
        var primeiroId = JSON.readTree(primeira).get("id").asText();

        // Reenvio: a resposta do primeiro envio se perdeu e a fila tentou de novo.
        mockMvc.perform(post("/api/v1/production/batches/" + batchId + "/measurements")
                        .session(session).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate", is(true)))
                // Devolve a medição GRAVADA: responder um id novo faria a fila guardar um id que não
                // existe no servidor.
                .andExpect(jsonPath("$.id", is(primeiroId)));

        var listadas = JSON.readTree(mockMvc.perform(
                        get("/api/v1/production/batches/" + batchId + "/measurements").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(listadas.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("PWA-002: apontamentos diferentes com o mesmo valor são medições diferentes")
    void offlineQueueDistinctKeysAreDistinctMeasurements() throws Exception {
        // Duas leituras iguais tiradas em sequência são dois fatos. Deduplicar por conteúdo descartaria
        // uma medição verdadeira — por isso a chave identifica o apontamento, não o valor.
        var session = login();
        var batchId = startedBatch(session).get("id").asText();

        for (var chave : new String[] {"a-" + UUID.randomUUID(), "b-" + UUID.randomUUID()}) {
            mockMvc.perform(post("/api/v1/production/batches/" + batchId + "/measurements")
                            .session(session).with(csrf()).contentType("application/json")
                            .content("{\"kind\":\"TEMPERATURE\",\"value\":66,\"unit\":\"C\","
                                    + "\"source\":\"MANUAL\",\"clientRequestId\":\"" + chave + "\"}"))
                    .andExpect(status().isCreated());
        }

        var listadas = JSON.readTree(mockMvc.perform(
                        get("/api/v1/production/batches/" + batchId + "/measurements").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(listadas.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("PWA-002: registro sem chave continua funcionando e nunca é tratado como repetição")
    void withoutClientRequestIdEveryRecordIsNew() throws Exception {
        // Registro pela tela, com rede: a requisição é síncrona e quem a fez viu a resposta.
        var session = login();
        var batchId = startedBatch(session).get("id").asText();
        var body = "{\"kind\":\"TEMPERATURE\",\"value\":66,\"unit\":\"C\",\"source\":\"MANUAL\"}";

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/production/batches/" + batchId + "/measurements")
                            .session(session).with(csrf()).contentType("application/json").content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.duplicate", is(false)));
        }

        var listadas = JSON.readTree(mockMvc.perform(
                        get("/api/v1/production/batches/" + batchId + "/measurements").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(listadas.size()).isEqualTo(2);
    }

    @Test
    void rejectsUnitIncompatibleWithKind() throws Exception {
        var session = login();
        var batchId = startedBatch(session).get("id").asText();

        mockMvc.perform(post("/api/v1/production/batches/" + batchId + "/measurements").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"kind\":\"DENSITY\",\"value\":10,\"unit\":\"C\",\"source\":\"MANUAL\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsStepNotInBatch() throws Exception {
        var session = login();
        var batchId = startedBatch(session).get("id").asText();

        mockMvc.perform(post("/api/v1/production/batches/" + batchId + "/measurements").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"stepId\":\"" + UUID.randomUUID() + "\",\"kind\":\"TEMPERATURE\",\"value\":66,"
                                + "\"unit\":\"C\",\"source\":\"MANUAL\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deniesRecordWithoutPermission() throws Exception {
        var session = login();
        var batchId = startedBatch(session).get("id").asText();

        mockMvc.perform(post("/api/v1/production/batches/" + batchId + "/measurements")
                        .with(authentication(principal(UUID.randomUUID(), Set.of("production.batch.read")))).with(csrf())
                        .contentType("application/json")
                        .content("{\"kind\":\"PH\",\"value\":5.2,\"unit\":\"PH\",\"source\":\"MANUAL\"}"))
                .andExpect(status().isForbidden());
    }

    // --- helpers ---

    private JsonNode startedBatch(MockHttpSession session) throws Exception {
        var orderId = releasedOrder(session);
        mockMvc.perform(post("/api/v1/brew-orders/" + orderId + "/start").session(session).with(csrf()))
                .andExpect(status().isOk());
        var listBody = mockMvc.perform(get("/api/v1/production/batches").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String batchId = null;
        // A listagem passou a ser paginada (REL-002): o array vem em `content`.
        for (var node : JSON.readTree(listBody).get("content")) {
            if (node.get("orderId").asText().equals(orderId)) {
                batchId = node.get("id").asText();
            }
        }
        var detail = mockMvc.perform(get("/api/v1/production/batches/" + batchId).session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(detail);
    }

    private String releasedOrder(MockHttpSession session) throws Exception {
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var equipmentId = idOf(mockMvc.perform(post("/api/v1/equipment").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"bh-" + sfx + "\",\"name\":\"BH\",\"capacityLiters\":500,"
                                + "\"deadSpaceLiters\":20,\"mashEfficiencyPercent\":72,\"boilOffLitersPerHour\":8}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        var maltId = createIngredient(session, "MALT", "m-" + sfx, "KG", "{\"potentialSg\":\"1.037\",\"colorEbc\":\"4\"}");
        var hopId = createIngredient(session, "HOP", "h-" + sfx, "G", "{\"alphaAcid\":\"12\"}");
        var yeastId = createIngredient(session, "YEAST", "y-" + sfx, "UNIT", "{\"attenuation\":\"78\"}");
        var content = """
                {"name":"Meas %s","equipmentId":"%s","batchVolumeLiters":400,"boilTimeMinutes":60,"targetIbu":30,
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

    private String createIngredient(MockHttpSession session, String type, String code, String unit, String attributes)
            throws Exception {
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
