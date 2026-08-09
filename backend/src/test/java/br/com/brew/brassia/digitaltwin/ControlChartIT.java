package br.com.brew.brassia.digitaltwin;

import static org.assertj.core.api.Assertions.assertThat;
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

/**
 * A carta de controle de ponta a ponta (SPC-001).
 *
 * <p>O que só aparece aqui: a série sai do banco pela consulta publicada nova
 * ({@code production.BatchMeasurementLookup}), <strong>ordenada por instante de medição</strong> — e a
 * ordem é o que faz sequência e tendência significarem alguma coisa. Um dublê imita a ordenação; só o SQL
 * a prova.
 *
 * <p>As medições são registradas pela API real, uma a uma, sobre um lote realmente produzido. É caro, e é o
 * ponto: um gráfico de controle construído sobre dados inventados testaria a aritmética, não a integração.
 */
@SpringBootTest
@Testcontainers
class ControlChartIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String CHARTS = "/api/v1/digital-twin/control-charts";

    @Autowired WebApplicationContext context;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("processo estável: 20 medições, limites calculados e nenhum sinal")
    void estavelSemSinal() throws Exception {
        var session = login();
        var batch = startedBatch(session);
        var batchId = batch.get("id").asText();
        var recipeId = batch.get("recipeId").asText();
        for (int i = 0; i < 20; i++) {
            recordTemperature(session, batchId, i % 2 == 0 ? "19" : "21");
        }

        var chart = read(analyze(session, recipeId, batchId).andExpect(status().isOk()));

        assertThat(chart.get("points")).hasSize(20);
        assertThat(chart.get("controlLimits").get("centerLine").decimalValue())
                .isEqualByComparingTo("20");
        assertThat(chart.get("inControl").asBoolean()).isTrue();
        assertThat(chart.get("signals")).isEmpty();
        assertThat(chart.get("unit").asText()).isEqualTo("C");
    }

    @Test
    @DisplayName("um ponto além de 3σ é sinalizado, e a carta sai do controle")
    void pontoForaSinalizado() throws Exception {
        var session = login();
        var batch = startedBatch(session);
        var batchId = batch.get("id").asText();
        var recipeId = batch.get("recipeId").asText();
        for (int i = 0; i < 20; i++) {
            recordTemperature(session, batchId, i % 2 == 0 ? "19" : "21");
        }
        recordTemperature(session, batchId, "40");

        var chart = read(analyze(session, recipeId, batchId).andExpect(status().isOk()));

        assertThat(chart.get("inControl").asBoolean()).isFalse();
        assertThat(chart.toString()).contains("BEYOND_LIMIT");
    }

    @Test
    @DisplayName("DESLOCAMENTO detectado com todos os pontos dentro dos limites")
    void deslocamentoSemPontoFora() throws Exception {
        // O caso que a inspeção ponto a ponto não pega: o processo mudou de patamar e continua estável.
        var session = login();
        var batch = startedBatch(session);
        var batchId = batch.get("id").asText();
        var recipeId = batch.get("recipeId").asText();
        for (int i = 0; i < 20; i++) {
            recordTemperature(session, batchId, i % 2 == 0 ? "19" : "21");
        }
        recordTemperature(session, batchId, "19");
        for (int i = 0; i < 7; i++) {
            recordTemperature(session, batchId, "20.5");
        }

        var chart = read(analyze(session, recipeId, batchId).andExpect(status().isOk()));

        assertThat(chart.toString()).contains("RUN_ON_ONE_SIDE");
        assertThat(chart.toString()).doesNotContain("BEYOND_LIMIT");
    }

    @Test
    @DisplayName("A ORDEM VEM DO BANCO: os pontos saem cronológicos")
    void ordemCronologicaVemDoBanco() throws Exception {
        // Sequência e tendência só existem no tempo. Um dublê imita a ordenação; só o SQL a prova.
        var session = login();
        var batch = startedBatch(session);
        var batchId = batch.get("id").asText();
        var recipeId = batch.get("recipeId").asText();
        for (int i = 0; i < 20; i++) {
            recordTemperature(session, batchId, String.valueOf(19 + (i % 3)));
        }

        var chart = read(analyze(session, recipeId, batchId).andExpect(status().isOk()));

        String anterior = null;
        for (var point : chart.get("points")) {
            var atual = point.get("measuredAt").asText();
            if (anterior != null) {
                assertThat(atual).isGreaterThanOrEqualTo(anterior);
            }
            anterior = atual;
        }
    }

    @Test
    @DisplayName("menos de 20 medições responde 422 dizendo QUANTAS faltam")
    void historicoCurtoResponde422() throws Exception {
        // A providência é concreta: medir mais, ou incluir mais lotes.
        var session = login();
        var batch = startedBatch(session);
        var batchId = batch.get("id").asText();
        var recipeId = batch.get("recipeId").asText();
        for (int i = 0; i < 5; i++) {
            recordTemperature(session, batchId, "20");
        }

        analyze(session, recipeId, batchId)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("insufficient_control_history"))
                .andExpect(jsonPath("$.available").value(5))
                .andExpect(jsonPath("$.required").value(20));
    }

    @Test
    @DisplayName("lote de outra cervejaria não entra na série")
    void isolaPorCervejaria() throws Exception {
        var session = login();
        var batch = startedBatch(session);
        var batchId = batch.get("id").asText();
        var recipeId = batch.get("recipeId").asText();
        for (int i = 0; i < 20; i++) {
            recordTemperature(session, batchId, "20");
        }

        var outra = principal(UUID.randomUUID(), Set.of("digitaltwin.profile.read"));

        // Sem os pontos do lote alheio, não há histórico: 422 em vez de uma carta com dados de fora.
        mockMvc.perform(post(CHARTS).with(csrf()).with(authentication(outra))
                        .contentType("application/json")
                        .content(body(recipeId, batchId)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("sem permissão, nada responde")
    void semPermissaoNadaResponde() throws Exception {
        var nobody = principal(UUID.randomUUID(), Set.of());

        mockMvc.perform(post(CHARTS).with(csrf()).with(authentication(nobody))
                        .contentType("application/json")
                        .content(body(UUID.randomUUID().toString(), UUID.randomUUID().toString())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("lista de lotes vazia é recusada no contrato")
    void listaVaziaRecusada() throws Exception {
        var session = login();

        mockMvc.perform(post(CHARTS).with(csrf()).session(session)
                        .contentType("application/json")
                        .content("{\"recipeId\":\"" + UUID.randomUUID()
                                + "\",\"kind\":\"TEMPERATURE\",\"batchIds\":[]}"))
                .andExpect(status().isBadRequest());
    }

    // --- infraestrutura ---

    private org.springframework.test.web.servlet.ResultActions analyze(MockHttpSession session,
            String recipeId, String batchId) throws Exception {
        return mockMvc.perform(post(CHARTS).with(csrf()).session(session)
                .contentType("application/json").content(body(recipeId, batchId)));
    }

    private static String body(String recipeId, String batchId) {
        return "{\"recipeId\":\"" + recipeId + "\",\"kind\":\"TEMPERATURE\",\"batchIds\":[\""
                + batchId + "\"]}";
    }

    private void recordTemperature(MockHttpSession session, String batchId, String value)
            throws Exception {
        mockMvc.perform(post("/api/v1/production/batches/" + batchId + "/measurements")
                        .session(session).with(csrf()).contentType("application/json")
                        .content("{\"kind\":\"TEMPERATURE\",\"value\":" + value
                                + ",\"unit\":\"C\",\"source\":\"MANUAL\"}"))
                .andExpect(status().isCreated());
    }

    private JsonNode startedBatch(MockHttpSession session) throws Exception {
        var orderId = releasedOrder(session);
        mockMvc.perform(post("/api/v1/brew-orders/" + orderId + "/start").session(session).with(csrf()))
                .andExpect(status().isOk());
        var listBody = mockMvc.perform(get("/api/v1/production/batches").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String batchId = null;
        for (var node : JSON.readTree(listBody)) {
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
                        .content("{\"code\":\"spc-" + sfx + "\",\"name\":\"BH\",\"capacityLiters\":500,"
                                + "\"deadSpaceLiters\":20,\"mashEfficiencyPercent\":72,"
                                + "\"boilOffLitersPerHour\":8}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        var maltId = createIngredient(session, "MALT", "m-" + sfx, "KG",
                "{\"potentialSg\":\"1.037\",\"colorEbc\":\"4\"}");
        var hopId = createIngredient(session, "HOP", "h-" + sfx, "G", "{\"alphaAcid\":\"12\"}");
        var yeastId = createIngredient(session, "YEAST", "y-" + sfx, "UNIT", "{\"attenuation\":\"78\"}");
        var content = """
                {"name":"SPC %s","equipmentId":"%s","batchVolumeLiters":400,"boilTimeMinutes":60,
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
        return orderId;
    }

    private String createIngredient(MockHttpSession session, String type, String code, String unit,
            String profile) throws Exception {
        return idOf(mockMvc.perform(post("/api/v1/catalog/ingredients").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"type\":\"" + type + "\",\"code\":\"" + code + "\",\"name\":\"" + code
                                + "\",\"useUnit\":\"" + unit + "\",\"purchaseUnit\":\"" + unit
                                + "\",\"attributes\":" + profile + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private static String idOf(String body) throws Exception {
        return JSON.readTree(body).get("id").asText();
    }

    private JsonNode read(org.springframework.test.web.servlet.ResultActions actions) throws Exception {
        return JSON.readTree(actions.andReturn().getResponse().getContentAsString());
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
