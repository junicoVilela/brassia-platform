package br.com.brew.brassia;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Parametrização por cervejaria (PRM-001).
 *
 * <p>A suíte cobre as duas metades da invariante da história: o parâmetro <strong>muda o
 * comportamento quando existe</strong> e a <strong>ausência dele preserva exatamente o que havia
 * antes</strong>.
 */
@SpringBootTest
@Testcontainers
class BreweryParametersIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);

    @Autowired WebApplicationContext context;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    // --- limpeza ---

    @Test
    void aPoliticaDeLimpezaNasceSemPrazo() throws Exception {
        var session = login();

        mockMvc.perform(get("/api/v1/sanitation/cleaning-policy").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validityHours").doesNotExist())
                .andExpect(jsonPath("$.expiresByTime", is(false)));
    }

    @Test
    void configurarERemoverOPrazoDeLimpeza() throws Exception {
        var session = login();

        mockMvc.perform(put("/api/v1/sanitation/cleaning-policy").session(session).with(csrf())
                        .contentType("application/json").content("{\"validityHours\":24}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validityHours", is(24)))
                .andExpect(jsonPath("$.expiresByTime", is(true)));

        // Voltar a nulo restaura o comportamento anterior à PRM-001.
        mockMvc.perform(put("/api/v1/sanitation/cleaning-policy").session(session).with(csrf())
                        .contentType("application/json").content("{\"validityHours\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresByTime", is(false)));
    }

    @Test
    void recusaPrazoDeLimpezaInvalido() throws Exception {
        var session = login();

        mockMvc.perform(put("/api/v1/sanitation/cleaning-policy").session(session).with(csrf())
                        .contentType("application/json").content("{\"validityHours\":0}"))
                .andExpect(status().isBadRequest());
    }

    // --- gás: o parâmetro deriva o vencimento ---

    @Test
    void semPoliticaDeGasARequalificacaoExigeADataInformada() throws Exception {
        var session = login();
        // A suíte compartilha a cervejaria de bootstrap, então "sem política" precisa ser
        // declarado, não presumido — outro teste desta classe configura a periodicidade.
        mockMvc.perform(put("/api/v1/gas/policy").session(session).with(csrf())
                        .contentType("application/json").content("{\"requalificationMonths\":null}"))
                .andExpect(status().isOk());
        var cylinder = createCylinder(session);

        mockMvc.perform(post("/api/v1/gas/cylinders/" + cylinder + "/requalification").session(session)
                        .with(csrf()).contentType("application/json").content("{\"dueOn\":null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void comPoliticaDeGasOVencimentoEhDerivado() throws Exception {
        var session = login();
        var cylinder = createCylinder(session);
        mockMvc.perform(put("/api/v1/gas/policy").session(session).with(csrf())
                        .contentType("application/json").content("{\"requalificationMonths\":60}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.derivesDueDate", is(true)));

        mockMvc.perform(post("/api/v1/gas/cylinders/" + cylinder + "/requalification").session(session)
                        .with(csrf()).contentType("application/json").content("{\"dueOn\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requalificationDueOn", is(TODAY.plusMonths(60).toString())));
    }

    @Test
    void aDataInformadaSempreVenceSobreAPolitica() throws Exception {
        var session = login();
        var cylinder = createCylinder(session);
        mockMvc.perform(put("/api/v1/gas/policy").session(session).with(csrf())
                        .contentType("application/json").content("{\"requalificationMonths\":60}"));

        var informada = TODAY.plusYears(2);
        mockMvc.perform(post("/api/v1/gas/cylinders/" + cylinder + "/requalification").session(session)
                        .with(csrf()).contentType("application/json")
                        .content("{\"dueOn\":\"" + informada + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requalificationDueOn", is(informada.toString())));
    }

    // --- metrologia: periodicidade por tipo ---

    @Test
    void aPeriodicidadeDeCalibracaoEhPorTipo() throws Exception {
        var session = login();

        mockMvc.perform(put("/api/v1/metrology/calibration-policy").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"monthsByType\":{\"THERMOMETER\":12,\"SCALE\":24}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthsByType.THERMOMETER", is(12)))
                .andExpect(jsonPath("$.monthsByType.SCALE", is(24)));

        mockMvc.perform(get("/api/v1/metrology/calibration-policy").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthsByType.PH_METER").doesNotExist());
    }

    // --- qualidade: prazos por severidade ---

    @Test
    void osPrazosDoCapaSaoPorSeveridade() throws Exception {
        var session = login();

        mockMvc.perform(put("/api/v1/quality/capa-policy").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"bySeverity":{"CRITICAL":{"containmentDays":1,"investigationDays":3,
                                 "verificationDays":15}}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bySeverity.CRITICAL.containmentDays", is(1)))
                .andExpect(jsonPath("$.bySeverity.MINOR").doesNotExist());
    }

    @Test
    void osPrazosDoCapaSeguemAOrdemDasFases() throws Exception {
        var session = login();

        mockMvc.perform(put("/api/v1/quality/capa-policy").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"bySeverity":{"MAJOR":{"containmentDays":10,"investigationDays":5,
                                 "verificationDays":30}}}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void comPoliticaDeCapaOsPrazosDaNaoConformidadeSaoDerivados() throws Exception {
        var session = login();
        mockMvc.perform(put("/api/v1/quality/capa-policy").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"bySeverity":{"MAJOR":{"containmentDays":2,"investigationDays":7,
                                 "verificationDays":30}}}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/quality/non-conformities").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"code":"NC-%s","title":"t","description":"d","source":"AUDIT",
                                 "deviationId":null,"severity":"MAJOR","containmentDueOn":null,
                                 "investigationDueOn":null,"verificationDueOn":null}
                                """.formatted(suffix())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.containmentDueOn", is(TODAY.plusDays(2).toString())))
                .andExpect(jsonPath("$.verificationDueOn", is(TODAY.plusDays(30).toString())));
    }

    @Test
    void semPoliticaDeCapaOsPrazosContinuamObrigatorios() throws Exception {
        var session = login();
        // Mesmo motivo: a ausência de política é declarada, não presumida.
        mockMvc.perform(put("/api/v1/quality/capa-policy").session(session).with(csrf())
                        .contentType("application/json").content("{\"bySeverity\":{}}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/quality/non-conformities").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"code":"NC-%s","title":"t","description":"d","source":"AUDIT",
                                 "deviationId":null,"severity":"CRITICAL","containmentDueOn":null,
                                 "investigationDueOn":null,"verificationDueOn":null}
                                """.formatted(suffix())))
                .andExpect(status().isBadRequest());
    }

    // --- sensorial: a escala é congelada na sessão ---

    @Test
    void aEscalaSensorialPadraoEhDez() throws Exception {
        var session = login();

        mockMvc.perform(get("/api/v1/sensory/policy").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxScore", is(10)))
                .andExpect(jsonPath("$.appliesToNewSessionsOnly", is(true)));
    }

    @Test
    void mudarAEscalaNaoReinterpretaSessaoJaCriada() throws Exception {
        var session = login();
        // Sessão criada na escala 10; nota 8 é válida.
        var antiga = createSensorySession(session);
        var amostraAntiga = addSampleAndOpen(session, antiga);
        evaluate(session, antiga, amostraAntiga, 8).andExpect(status().isCreated());

        mockMvc.perform(put("/api/v1/sensory/policy").session(session).with(csrf())
                        .contentType("application/json").content("{\"maxScore\":50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxScore", is(50)));

        // A sessão antiga continua na escala 10: nota 30 é recusada nela.
        var outroProvador = principal(UUID.randomUUID(), Set.of("sensory.evaluation.submit"));
        mockMvc.perform(post("/api/v1/sensory/sessions/" + antiga + "/evaluations").with(csrf())
                        .with(authentication(outroProvador)).contentType("application/json")
                        .content(scoresBody(amostraAntiga, 30)))
                .andExpect(status().isBadRequest());

        // Uma sessão nova nasce na escala 50 e aceita 30.
        var nova = createSensorySession(session);
        var amostraNova = addSampleAndOpen(session, nova);
        evaluate(session, nova, amostraNova, 30).andExpect(status().isCreated());
    }

    @Test
    void recusaEscalaSensorialInvalida() throws Exception {
        var session = login();

        mockMvc.perform(put("/api/v1/sensory/policy").session(session).with(csrf())
                        .contentType("application/json").content("{\"maxScore\":2}"))
                .andExpect(status().isBadRequest());
    }

    // --- autorização e tenant ---

    @Test
    void ajustarParametroEhAlcadaPropria() throws Exception {
        var brewery = UUID.randomUUID();

        // Ler o ciclo de limpeza não dá direito de mudar a política.
        mockMvc.perform(put("/api/v1/sanitation/cleaning-policy").with(csrf())
                        .with(authentication(principal(brewery, Set.of("sanitation.cycle.read"))))
                        .contentType("application/json").content("{\"validityHours\":24}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/gas/policy").with(csrf())
                        .with(authentication(principal(brewery, Set.of("gas.read", "gas.manage"))))
                        .contentType("application/json").content("{\"requalificationMonths\":60}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/sensory/policy").with(csrf())
                        .with(authentication(principal(brewery, Set.of("sensory.session.manage"))))
                        .contentType("application/json").content("{\"maxScore\":50}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aPoliticaDeUmaCervejariaNaoVazaParaOutra() throws Exception {
        var session = login();
        mockMvc.perform(put("/api/v1/sanitation/cleaning-policy").session(session).with(csrf())
                        .contentType("application/json").content("{\"validityHours\":48}"))
                .andExpect(status().isOk());

        // Outra cervejaria vê a política vazia, não a de 48 horas.
        mockMvc.perform(get("/api/v1/sanitation/cleaning-policy")
                        .with(authentication(principal(UUID.randomUUID(), Set.of("sanitation.cycle.read")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresByTime", is(false)));
    }

    // --- helpers ---

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private String createCylinder(MockHttpSession session) throws Exception {
        var body = mockMvc.perform(post("/api/v1/gas/cylinders").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"code":"CIL-%s","gasType":"CO2","capacityKg":25,"tareKg":38,
                                 "contentKg":25,"requalificationDueOn":"%s","location":"Casa de gases"}
                                """.formatted(suffix(), TODAY.plusYears(3))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("id").asText();
    }

    private String createSensorySession(MockHttpSession session) throws Exception {
        var body = mockMvc.perform(post("/api/v1/sensory/sessions").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"code":"SEN-%s","purpose":"Escala","scheduledFor":"%s"}
                                """.formatted(suffix(), TODAY)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("id").asText();
    }

    private String addSampleAndOpen(MockHttpSession session, String sessionId) throws Exception {
        mockMvc.perform(post("/api/v1/sensory/sessions/" + sessionId + "/samples").session(session)
                        .with(csrf()).contentType("application/json")
                        .content("{\"batchId\":\"" + fermentingBatch(session) + "\",\"note\":null}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/sensory/sessions/" + sessionId + "/open").session(session).with(csrf()))
                .andExpect(status().isOk());
        var body = mockMvc.perform(get("/api/v1/sensory/sessions/" + sessionId).session(session))
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("samples").get(0).get("id").asText();
    }

    private static String scoresBody(String sampleId, int score) {
        return """
                {"sampleId":"%s","scores":{"APPEARANCE":%d,"AROMA":%d,"FLAVOR":%d,"BODY":%d,
                 "OVERALL":%d},"descriptors":[],"note":null}
                """.formatted(sampleId, score, score, score, score, score);
    }

    private org.springframework.test.web.servlet.ResultActions evaluate(MockHttpSession session,
            String sessionId, String sampleId, int score) throws Exception {
        return mockMvc.perform(post("/api/v1/sensory/sessions/" + sessionId + "/evaluations")
                .session(session).with(csrf()).contentType("application/json")
                .content(scoresBody(sampleId, score)));
    }

    private String fermentingBatch(MockHttpSession session) throws Exception {
        var equipmentId = createEquipment(session);
        var maltId = createIngredient(session, "MALT", "KG", "{\"potentialSg\":\"1.037\",\"colorEbc\":\"4\"}");
        var hopId = createIngredient(session, "HOP", "G", "{\"alphaAcid\":\"12\"}");
        var yeastId = createIngredient(session, "YEAST", "UNIT", "{\"attenuation\":\"78\"}");
        var recipeId = idOf(mockMvc.perform(post("/api/v1/recipes").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"name":"Param %s","equipmentId":"%s","batchVolumeLiters":400,
                                 "boilTimeMinutes":60,"targetIbu":30,
                                 "items":[{"ingredientId":"%s","stage":"MASH","quantity":20,"unit":"KG"},
                                          {"ingredientId":"%s","stage":"BOIL","quantity":60,"unit":"G",
                                           "timingMinutes":60},
                                          {"ingredientId":"%s","stage":"FERMENTATION","quantity":1,
                                           "unit":"UNIT"}]}
                                """.formatted(suffix(), equipmentId, maltId, hopId, yeastId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/v1/recipes/" + recipeId + "/metrics").session(session).with(csrf()));
        mockMvc.perform(post("/api/v1/recipes/" + recipeId + "/publish").session(session).with(csrf()));
        var orderId = idOf(mockMvc.perform(post("/api/v1/brew-orders").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"recipeId\":\"" + recipeId + "\",\"volumeLiters\":400}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/v1/brew-orders/" + orderId + "/release").session(session).with(csrf())
                .contentType("application/json")
                .content("{\"assignedUserId\":\"" + UUID.randomUUID() + "\"}"));
        mockMvc.perform(post("/api/v1/brew-orders/" + orderId + "/start").session(session).with(csrf()));
        var listBody = mockMvc.perform(get("/api/v1/production/batches").session(session))
                .andReturn().getResponse().getContentAsString();
        // A listagem passou a ser paginada (REL-002): o array vem em `content`.
        for (var node : JSON.readTree(listBody).get("content")) {
            if (node.get("orderId").asText().equals(orderId)) {
                return node.get("id").asText();
            }
        }
        throw new AssertionError("lote não encontrado");
    }

    private String createEquipment(MockHttpSession session) throws Exception {
        return idOf(mockMvc.perform(post("/api/v1/equipment").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"EQ-" + suffix() + "\",\"name\":\"Tanque\","
                                + "\"capacityLiters\":500,\"deadSpaceLiters\":20,"
                                + "\"mashEfficiencyPercent\":72,\"boilOffLitersPerHour\":8}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private String createIngredient(MockHttpSession session, String type, String unit, String attributes)
            throws Exception {
        var code = type.toLowerCase(java.util.Locale.ROOT).charAt(0) + "-" + suffix();
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
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private Authentication principal(UUID breweryId, Set<String> permissions) {
        var p = new SecurityPrincipal(UUID.randomUUID(), breweryId, "Tester", permissions);
        return new UsernamePasswordAuthenticationToken(p, "n/a", Set.of());
    }
}
