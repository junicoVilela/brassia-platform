package br.com.brew.brassia.sensory;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import br.com.brew.brassia.shared.security.SecurityPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
class SensoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SESSIONS = "/api/v1/sensory/sessions";
    private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);

    @Autowired WebApplicationContext context;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    // --- montagem da sessão ---

    @Test
    void aSessaoNasceRascunhoSemAmostra() throws Exception {
        var session = login();
        var id = createSession(session);

        mockMvc.perform(get(SESSIONS + "/" + id).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("DRAFT")))
                .andExpect(jsonPath("$.samples.length()", is(0)))
                .andExpect(jsonPath("$.resultsAvailable", is(false)));
    }

    @Test
    void aAmostraGanhaCodigoCegoDeTresDigitosSorteadoPeloSistema() throws Exception {
        var session = login();
        var id = createSession(session);
        var batch = fermentingBatch(session);

        addSample(session, id, batch)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.samples[0].blindCode", notNullValue()))
                // Enquanto não fecha, o lote não sai na resposta: a cegueira é da API, não da tela.
                .andExpect(jsonPath("$.samples[0].batchId").doesNotExist());

        var body = mockMvc.perform(get(SESSIONS + "/" + id).session(session))
                .andReturn().getResponse().getContentAsString();
        var blindCode = JSON.readTree(body).get("samples").get(0).get("blindCode").asText();
        org.assertj.core.api.Assertions.assertThat(blindCode).matches("\\d{3}");
    }

    @Test
    void sessaoSemAmostraNaoAbre() throws Exception {
        var session = login();
        var id = createSession(session);

        mockMvc.perform(post(SESSIONS + "/" + id + "/open").session(session).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void abertaNaoAceitaAmostraNova() throws Exception {
        var session = login();
        var id = openSessionWithSample(session);
        var batch = fermentingBatch(session);

        addSample(session, id, batch).andExpect(status().isConflict());
    }

    @Test
    void recusaLoteInexistente() throws Exception {
        var session = login();
        var id = createSession(session);

        addSample(session, id, UUID.randomUUID().toString()).andExpect(status().isBadRequest());
    }

    // --- o resultado não aparece antes do fechamento ---

    @Test
    void oResultadoEhRecusadoEnquantoASessaoNaoFecha() throws Exception {
        var session = login();
        var id = createSession(session);
        var batch = fermentingBatch(session);
        addSample(session, id, batch);

        mockMvc.perform(get(SESSIONS + "/" + id + "/results").session(session))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("results_not_available")))
                .andExpect(jsonPath("$.session.status", is("Rascunho")));

        mockMvc.perform(post(SESSIONS + "/" + id + "/open").session(session).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get(SESSIONS + "/" + id + "/results").session(session))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.session.status", is("Em avaliação")));
    }

    @Test
    void comASessaoAbertaSoONumeroDeFichasEhPublico() throws Exception {
        var session = login();
        var id = openSessionWithSample(session);
        var sample = firstSample(session, id);
        evaluate(session, id, sample, 8).andExpect(status().isCreated());

        mockMvc.perform(get(SESSIONS + "/" + id).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluationCount", is(1)))
                // Nem lote, nem nota, nem descritor saem daqui.
                .andExpect(jsonPath("$.samples[0].batchId").doesNotExist())
                .andExpect(jsonPath("$.samples[0].averages").doesNotExist());
    }

    @Test
    void oFechamentoRevelaLoteMediaEDispersao() throws Exception {
        var session = login();
        var id = openSessionWithSample(session);
        var sample = firstSample(session, id);
        evaluate(session, id, sample, 8);

        mockMvc.perform(post(SESSIONS + "/" + id + "/close").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CLOSED")))
                .andExpect(jsonPath("$.resultsAvailable", is(true)))
                // Agora sim o lote aparece — e nunca tinha sido apagado.
                .andExpect(jsonPath("$.samples[0].batchId", notNullValue()));

        mockMvc.perform(get(SESSIONS + "/" + id + "/results").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.samples.length()", is(1)))
                .andExpect(jsonPath("$.samples[0].evaluations", is(1)))
                .andExpect(jsonPath("$.samples[0].overallAverage", is(8.0)))
                .andExpect(jsonPath("$.samples[0].batchId", notNullValue()));
    }

    // --- ficha ---

    @Test
    void umProvadorUmaFichaPorAmostra() throws Exception {
        var session = login();
        var id = openSessionWithSample(session);
        var sample = firstSample(session, id);

        evaluate(session, id, sample, 8).andExpect(status().isCreated());
        evaluate(session, id, sample, 3)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("already_evaluated")));
    }

    @Test
    void aFichaSoEntraComASessaoAberta() throws Exception {
        var session = login();
        var id = createSession(session);
        var batch = fermentingBatch(session);
        addSample(session, id, batch);
        var sample = firstSample(session, id);

        evaluate(session, id, sample, 8)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("session_not_open")));

        mockMvc.perform(post(SESSIONS + "/" + id + "/open").session(session).with(csrf()));
        evaluate(session, id, sample, 8).andExpect(status().isCreated());

        mockMvc.perform(post(SESSIONS + "/" + id + "/close").session(session).with(csrf()));
        evaluate(session, id, sample, 8).andExpect(status().isConflict());
    }

    @Test
    void aFichaExigeTodosOsAtributosNaFaixa() throws Exception {
        var session = login();
        var id = openSessionWithSample(session);
        var sample = firstSample(session, id);

        // Sem o atributo BODY.
        mockMvc.perform(post(SESSIONS + "/" + id + "/evaluations").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"sampleId":"%s","scores":{"APPEARANCE":8,"AROMA":8,"FLAVOR":8,"OVERALL":8},
                                 "descriptors":[],"note":null}
                                """.formatted(sample)))
                .andExpect(status().isBadRequest());

        // Nota fora da escala.
        evaluate(session, id, sample, 11).andExpect(status().isBadRequest());
    }

    // --- viés do painel ---

    @Test
    void aComparacaoAcusaViesQuandoOMesmoLoteRecebeNotasDistintas() throws Exception {
        var session = login();
        var id = createSession(session);
        var batch = fermentingBatch(session);
        // Duplicata cega: o mesmo lote sob dois códigos.
        addSample(session, id, batch).andExpect(status().isCreated());
        addSample(session, id, batch).andExpect(status().isCreated());
        mockMvc.perform(post(SESSIONS + "/" + id + "/open").session(session).with(csrf()))
                .andExpect(status().isOk());

        var body = mockMvc.perform(get(SESSIONS + "/" + id).session(session))
                .andReturn().getResponse().getContentAsString();
        var primeira = JSON.readTree(body).get("samples").get(0).get("id").asText();
        var segunda = JSON.readTree(body).get("samples").get(1).get("id").asText();

        evaluate(session, id, primeira, 9).andExpect(status().isCreated());
        evaluate(session, id, segunda, 4).andExpect(status().isCreated());
        mockMvc.perform(post(SESSIONS + "/" + id + "/close").session(session).with(csrf()));

        // A cerveja era a mesma; a diferença de 5 pontos é do painel.
        mockMvc.perform(get(SESSIONS + "/" + id + "/results").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consistency.length()", is(1)))
                .andExpect(jsonPath("$.consistency[0].difference", is(5.0)))
                .andExpect(jsonPath("$.consistency[0].blindCodes.length()", is(2)));
    }

    // --- autorização e tenant ---

    @Test
    void recusaSemPermissao() throws Exception {
        var brewery = UUID.randomUUID();

        mockMvc.perform(get(SESSIONS).with(authentication(principal(brewery, Set.of()))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(SESSIONS).with(csrf())
                        .with(authentication(principal(brewery, Set.of("sensory.session.read"))))
                        .contentType("application/json")
                        .content("{\"code\":\"S-X\",\"purpose\":\"p\",\"scheduledFor\":\"2026-08-03\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void naoEnxergaSessaoDeOutraCervejaria() throws Exception {
        var session = login();
        var id = createSession(session);
        var outra = UUID.randomUUID();

        mockMvc.perform(get(SESSIONS + "/" + id)
                        .with(authentication(principal(outra, Set.of("sensory.session.read")))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get(SESSIONS).with(authentication(principal(outra, Set.of("sensory.session.read")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(0)));
    }

    // --- helpers ---

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private String createSession(MockHttpSession session) throws Exception {
        var body = mockMvc.perform(post(SESSIONS).session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"code":"SEN-%s","purpose":"Comparativo de lote","scheduledFor":"%s"}
                                """.formatted(suffix(), TODAY)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("id").asText();
    }

    private ResultActions addSample(MockHttpSession session, String sessionId, String batchId)
            throws Exception {
        return mockMvc.perform(post(SESSIONS + "/" + sessionId + "/samples").session(session).with(csrf())
                .contentType("application/json")
                .content("{\"batchId\":\"" + batchId + "\",\"note\":null}"));
    }

    private String openSessionWithSample(MockHttpSession session) throws Exception {
        var id = createSession(session);
        addSample(session, id, fermentingBatch(session)).andExpect(status().isCreated());
        mockMvc.perform(post(SESSIONS + "/" + id + "/open").session(session).with(csrf()))
                .andExpect(status().isOk());
        return id;
    }

    private String firstSample(MockHttpSession session, String sessionId) throws Exception {
        var body = mockMvc.perform(get(SESSIONS + "/" + sessionId).session(session))
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("samples").get(0).get("id").asText();
    }

    private ResultActions evaluate(MockHttpSession session, String sessionId, String sampleId, int score)
            throws Exception {
        return mockMvc.perform(post(SESSIONS + "/" + sessionId + "/evaluations").session(session)
                .with(csrf()).contentType("application/json")
                .content("""
                        {"sampleId":"%s","scores":{"APPEARANCE":%d,"AROMA":%d,"FLAVOR":%d,"BODY":%d,
                         "OVERALL":%d},"descriptors":["cítrico"],"note":null}
                        """.formatted(sampleId, score, score, score, score, score)));
    }

    /** Lote transferido: é o estado em que a cerveja existe para ser provada. */
    private String fermentingBatch(MockHttpSession session) throws Exception {
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
        if (batchId == null) {
            throw new AssertionError("lote não encontrado para a ordem " + orderId);
        }
        mockMvc.perform(post("/api/v1/production/batches/" + batchId + "/transfer").session(session)
                        .with(csrf()).contentType("application/json")
                        .content("{\"destinationEquipmentId\":\"" + createEquipment(session)
                                + "\",\"volumeLiters\":390,\"ogSg\":1.052,\"lossesLiters\":8}"))
                .andExpect(status().isCreated());
        return batchId;
    }

    private String releasedOrder(MockHttpSession session) throws Exception {
        var sfx = suffix();
        var equipmentId = createEquipment(session);
        var maltId = createIngredient(session, "MALT", "KG", "{\"potentialSg\":\"1.037\",\"colorEbc\":\"4\"}");
        var hopId = createIngredient(session, "HOP", "G", "{\"alphaAcid\":\"12\"}");
        var yeastId = createIngredient(session, "YEAST", "UNIT", "{\"attenuation\":\"78\"}");
        var content = """
                {"name":"Sensorial %s","equipmentId":"%s","batchVolumeLiters":400,"boilTimeMinutes":60,
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

    private String createEquipment(MockHttpSession session) throws Exception {
        return idOf(mockMvc.perform(post("/api/v1/equipment").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"EQ-" + suffix() + "\",\"name\":\"Fermentador\","
                                + "\"capacityLiters\":500,\"deadSpaceLiters\":20,"
                                + "\"mashEfficiencyPercent\":72,\"boilOffLitersPerHour\":8}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private String createIngredient(MockHttpSession session, String type, String unit, String attributes)
            throws Exception {
        var code = type.toLowerCase(java.util.Locale.ROOT).charAt(0) + "-" + suffix();
        return idOf(mockMvc.perform(post("/api/v1/catalog/ingredients").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"type\":\"" + type + "\",\"code\":\"" + code + "\",\"name\":\""
                                + code + "\",\"useUnit\":\"" + unit + "\",\"purchaseUnit\":\"" + unit
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
}
