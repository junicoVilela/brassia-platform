package br.com.brew.brassia.quality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import br.com.brew.brassia.production.BatchAlertPublisher;
import br.com.brew.brassia.production.OpenBatchLookup;
import br.com.brew.brassia.quality.application.port.outbound.FrequencySweepRepository;
import br.com.brew.brassia.quality.application.service.FrequencySweepService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Varredura de cadência (QLT-001-A).
 *
 * <p>O relógio é injetado: sem isso, o teste teria de esperar horas ou adulterar a data do lote no banco
 * — e adulterar o banco testaria um estado que o sistema nunca produz.
 */
@SpringBootTest
@Testcontainers
class FrequencySweepIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String PLANS = "/api/v1/quality/control-plans";

    @Autowired WebApplicationContext context;
    @Autowired FrequencySweepRepository sweepRepository;
    @Autowired OpenBatchLookup openBatches;
    @Autowired BatchAlertPublisher alerts;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("CONTROLE ATRASADO VIRA ALERTA NO LOTE, e o mesmo atraso não avisa duas vezes")
    void atrasoViraAlertaUmaVezSo() throws Exception {
        var session = login();
        var cena = startedBatch(session);
        publishedPlanWith(session, cena.recipeId(), "PER_HOURS", "4");

        // Cinco horas depois do início, sem nenhuma medição: a primeira janela de 4 h passou.
        sweepAt(Duration.ofHours(5));

        var alertas = alertsOf(session, cena.batchId());
        assertThat(alertas).hasSize(1);
        assertThat(alertas.get(0).get("message").asText())
                .contains("Controle atrasado")
                .contains("pH do mosto");

        // Passando de novo na hora seguinte, o mesmo atraso NÃO vira aviso novo: central que repete o
        // mesmo aviso 24 vezes por dia é central que ninguém lê.
        sweepAt(Duration.ofHours(6));
        assertThat(alertsOf(session, cena.batchId())).hasSize(1);
    }

    @Test
    @DisplayName("SEM NENHUMA MEDIÇÃO, O ATRASO AVISA UMA VEZ SÓ — mesmo horas depois")
    void semMedicaoAvisaUmaVezSo() throws Exception {
        // A janela perdida continua sendo a mesma enquanto ninguém mede: "este ponto está atrasado" é um
        // fato só, e repeti-lo de hora em hora não acrescenta informação — só esvazia a central.
        var session = login();
        var cena = startedBatch(session);
        publishedPlanWith(session, cena.recipeId(), "PER_HOURS", "4");

        sweepAt(Duration.ofHours(5));
        sweepAt(Duration.ofHours(20));

        assertThat(alertsOf(session, cena.batchId())).hasSize(1);
    }

    @Test
    @DisplayName("MEDIR MOVE A JANELA: atrasar de novo depois disso é um fato novo e avisa de novo")
    void janelaNovaDepoisDeMedirAvisaDeNovo() throws Exception {
        var session = login();
        var cena = startedBatch(session);
        var plano = publishedPlanWith(session, cena.recipeId(), "PER_HOURS", "4");

        sweepAt(Duration.ofHours(5));
        assertThat(alertsOf(session, cena.batchId())).hasSize(1);

        // A medição chega: a janela passa a contar dela.
        medir(session, plano.planId(), plano.pointId(), cena.batchId());

        // E o ponto atrasa outra vez, muito depois. Agora sim é informação nova.
        sweepAt(Duration.ofHours(20));

        assertThat(alertsOf(session, cena.batchId())).hasSize(2);
    }

    @Test
    @DisplayName("CADÊNCIA QUE O RELÓGIO NÃO JULGA NÃO VIRA ALERTA")
    void cadenciaNaoJulgavelNaoAlerta() throws Exception {
        // PER_BATCH, PER_SHIFT e PER_PACKAGING_RUN continuam declaradas e não fiscalizadas — cobrá-las
        // exigiria inventar o significado delas (turno sem calendário de turnos, "por lote" num lote que
        // ainda não acabou).
        var session = login();
        var cena = startedBatch(session);
        publishedPlanWith(session, cena.recipeId(), "PER_BATCH", "null");

        sweepAt(Duration.ofHours(48));

        assertThat(alertsOf(session, cena.batchId())).isEmpty();
    }

    // --- infraestrutura ---

    /** Roda a varredura como se fosse `depois` do agora, com o relógio no lugar da espera. */
    private int sweepAt(Duration depois) {
        var clock = Clock.fixed(java.time.Instant.now().plus(depois), ZoneOffset.UTC);
        return new FrequencySweepService(sweepRepository, openBatches, alerts, clock).sweep();
    }

    private java.util.List<JsonNode> alertsOf(MockHttpSession session, String batchId) throws Exception {
        var body = mockMvc.perform(get("/api/v1/production/batches/" + batchId + "/alerts").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var lista = new java.util.ArrayList<JsonNode>();
        JSON.readTree(body).forEach(lista::add);
        return lista;
    }

    /**
     * Plano da RECEITA do lote, e não da casa.
     *
     * <p>A varredura é global por natureza — ela existe para achar o que ninguém está olhando. Um plano
     * sem receita (que vale para todos) alcançaria os lotes dos outros testes desta classe, e o teste
     * passaria a medir a poluição em vez da regra.
     */
    private record Plano(String planId, String pointId) {}

    private Plano publishedPlanWith(MockHttpSession session, String recipeId, String frequencyKind,
            String everyHours) throws Exception {
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var created = mockMvc.perform(post(PLANS).session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"code":"PCQ-%s","name":"Plano %s","recipeId":"%s","stage":"BREWING"}
                                """.formatted(sfx, sfx, recipeId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var planId = JSON.readTree(created).get("id").asText();

        mockMvc.perform(post(PLANS + "/" + planId + "/points").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"parameter":"pH do mosto","min":4.5,"max":5.5,"target":null,"unit":"pH",
                                 "frequencyKind":"%s","everyHours":%s,"action":"Medir e registrar",
                                 "severity":"MAJOR","critical":false}
                                """.formatted(frequencyKind, everyHours)))
                .andExpect(status().isCreated());
        mockMvc.perform(post(PLANS + "/" + planId + "/publish").session(session).with(csrf()))
                .andExpect(status().isOk());

        // O id do ponto vem do plano lido, e não da resposta de criação: é assim que a tela o obtém.
        var plano = mockMvc.perform(get(PLANS + "/" + planId).session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new Plano(planId, JSON.readTree(plano).get("points").get(0).get("id").asText());
    }

    private void medir(MockHttpSession session, String planId, String pointId, String batchId)
            throws Exception {
        mockMvc.perform(post("/api/v1/quality/measurements").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"planId":"%s","pointId":"%s","batchId":"%s","instrumentId":null,
                                 "value":5.0,"note":null,"measuredAt":null}
                                """.formatted(planId, pointId, batchId)))
                .andExpect(status().isCreated());
    }

    private record Cena(String batchId, String recipeId) {}

    private Cena startedBatch(MockHttpSession session) throws Exception {
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var equipmentId = idOf(mockMvc.perform(post("/api/v1/equipment").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"EQ-" + sfx + "\",\"name\":\"BH\",\"capacityLiters\":500,"
                                + "\"deadSpaceLiters\":20,\"mashEfficiencyPercent\":72,"
                                + "\"boilOffLitersPerHour\":8}"))
                .andExpect(status().isCreated()));
        var malte = ingrediente(session, "MALT", "m-" + sfx, "KG",
                "{\"potentialSg\":\"1.037\",\"colorEbc\":\"4\"}");
        var lupulo = ingrediente(session, "HOP", "h-" + sfx, "G", "{\"alphaAcid\":\"12\"}");
        var levedura = ingrediente(session, "YEAST", "y-" + sfx, "UNIT", "{\"attenuation\":\"78\"}");
        var receita = """
                {"name":"QLT %s","equipmentId":"%s","batchVolumeLiters":400,"boilTimeMinutes":60,
                 "targetIbu":30,
                 "items":[{"ingredientId":"%s","stage":"MASH","quantity":20,"unit":"KG"},
                          {"ingredientId":"%s","stage":"BOIL","quantity":60,"unit":"G","timingMinutes":60},
                          {"ingredientId":"%s","stage":"FERMENTATION","quantity":1,"unit":"UNIT"}]}
                """.formatted(sfx, equipmentId, malte, lupulo, levedura);
        var recipeId = idOf(mockMvc.perform(post("/api/v1/recipes").session(session).with(csrf())
                        .contentType("application/json").content(receita))
                .andExpect(status().isCreated()));
        mockMvc.perform(post("/api/v1/recipes/" + recipeId + "/metrics").session(session).with(csrf()));
        mockMvc.perform(post("/api/v1/recipes/" + recipeId + "/publish").session(session).with(csrf()));

        var orderId = idOf(mockMvc.perform(post("/api/v1/brew-orders").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"recipeId\":\"" + recipeId + "\",\"volumeLiters\":400}"))
                .andExpect(status().isCreated()));
        mockMvc.perform(post("/api/v1/brew-orders/" + orderId + "/release").session(session).with(csrf())
                .contentType("application/json")
                .content("{\"assignedUserId\":\"" + UUID.randomUUID() + "\"}"));
        mockMvc.perform(post("/api/v1/brew-orders/" + orderId + "/start").session(session).with(csrf()))
                .andExpect(status().isOk());

        var lista = mockMvc.perform(get("/api/v1/production/batches").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (JsonNode node : JSON.readTree(lista).get("content")) {
            if (node.get("orderId").asText().equals(orderId)) {
                return new Cena(node.get("id").asText(), recipeId);
            }
        }
        throw new AssertionError("lote não encontrado para a ordem " + orderId);
    }

    private String ingrediente(MockHttpSession session, String type, String code, String unit,
            String attributes) throws Exception {
        return idOf(mockMvc.perform(post("/api/v1/catalog/ingredients").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"type\":\"" + type + "\",\"code\":\"" + code + "\",\"name\":\"" + code
                                + "\",\"useUnit\":\"" + unit + "\",\"purchaseUnit\":\"" + unit
                                + "\",\"attributes\":" + attributes + "}"))
                .andExpect(status().isCreated()));
    }

    private String idOf(org.springframework.test.web.servlet.ResultActions actions) throws Exception {
        return JSON.readTree(actions.andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    private MockHttpSession login() throws Exception {
        var result = mockMvc.perform(post("/api/v1/security/login").with(csrf())
                        .contentType("application/json")
                        .content("{\"email\":\"admin@brassia.local\",\"password\":\"admin-local-123\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
