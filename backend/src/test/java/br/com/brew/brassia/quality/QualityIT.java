package br.com.brew.brassia.quality;

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
class QualityIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String PLANS = "/api/v1/quality/control-plans";
    private static final String MEASUREMENTS = "/api/v1/quality/measurements";
    private static final String DEVIATIONS = "/api/v1/quality/deviations";
    private static final String INSTRUMENTS = "/api/v1/metrology/instruments";
    private static final String STANDARDS = "/api/v1/metrology/standards";

    private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);

    @Autowired WebApplicationContext context;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    // --- plano ---

    @Test
    void planoNasceRascunhoENaoPublicaSemPonto() throws Exception {
        var session = login();
        var plan = createPlan(session);

        mockMvc.perform(get(PLANS + "/" + plan).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("DRAFT")))
                .andExpect(jsonPath("$.version", is(1)))
                .andExpect(jsonPath("$.points.length()", is(0)));

        mockMvc.perform(post(PLANS + "/" + plan + "/publish").session(session).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void limiteUnilateralEhAceito() throws Exception {
        var session = login();
        var plan = createPlan(session);

        // Só teto, como "O₂ ≤ 50 ppb".
        addPoint(session, plan, "Oxigênio dissolvido", "null", "50", "ppb", "MAJOR", false)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.points[0].limits", is("≤ 50 ppb")));
    }

    @Test
    void faixaSemNenhumLimiteEhRecusada() throws Exception {
        var session = login();
        var plan = createPlan(session);

        addPoint(session, plan, "Sem limite", "null", "null", "pH", "MINOR", false)
                .andExpect(status().isBadRequest());
    }

    @Test
    void publicadoEhImutavelEGeraNovaVersao() throws Exception {
        var session = login();
        var plan = publishedPlan(session, false);

        addPoint(session, plan, "Densidade", "1.000", "1.060", "SG", "MINOR", false)
                .andExpect(status().isConflict());

        var body = mockMvc.perform(post(PLANS + "/" + plan + "/new-version").session(session).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version", is(2)))
                .andExpect(jsonPath("$.status", is("DRAFT")))
                .andExpect(jsonPath("$.points.length()", is(1)))
                .andReturn().getResponse().getContentAsString();

        // A versão publicada continua intacta.
        mockMvc.perform(get(PLANS + "/" + plan).session(session))
                .andExpect(jsonPath("$.status", is("PUBLISHED")))
                .andExpect(jsonPath("$.version", is(1)));
        assertDifferentId(body, plan);
    }

    @Test
    void naoControlaOMesmoParametroDuasVezes() throws Exception {
        var session = login();
        var plan = createPlan(session);
        addPoint(session, plan, "pH do mosto", "4.5", "5.5", "pH", "MAJOR", false)
                .andExpect(status().isCreated());

        addPoint(session, plan, "PH DO MOSTO", "4.0", "6.0", "pH", "MINOR", false)
                .andExpect(status().isBadRequest());
    }

    // --- medição e desvio ---

    @Test
    void medicaoDentroDaFaixaNaoAbreDesvio() throws Exception {
        var session = login();
        var plan = publishedPlan(session, false);
        var point = firstPoint(session, plan);

        measure(session, plan, point, "5.0", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.withinSpec", is(true)))
                .andExpect(jsonPath("$.deviationId").doesNotExist());
    }

    @Test
    void medicaoForaDaFaixaAbreDesvioComASeveridadeDoPonto() throws Exception {
        var session = login();
        var plan = publishedPlan(session, false);
        var point = firstPoint(session, plan);

        measure(session, plan, point, "6.2", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.withinSpec", is(false)))
                .andExpect(jsonPath("$.deviationId", notNullValue()))
                .andExpect(jsonPath("$.deviation.severity", is("MAJOR")))
                .andExpect(jsonPath("$.deviation.bound", is("ABOVE_MAX")))
                .andExpect(jsonPath("$.deviation.limitValue", is(5.5)))
                .andExpect(jsonPath("$.deviation.excess", is(0.7)))
                .andExpect(jsonPath("$.deviation.action", is("Ajustar e remedir")));
    }

    @Test
    void oLimiteEhInclusivo() throws Exception {
        var session = login();
        var plan = publishedPlan(session, false);
        var point = firstPoint(session, plan);

        // Exatamente no teto: conforme. O limite é o último valor aceitável.
        measure(session, plan, point, "5.5", null).andExpect(jsonPath("$.withinSpec", is(true)));
        measure(session, plan, point, "4.5", null).andExpect(jsonPath("$.withinSpec", is(true)));
        measure(session, plan, point, "5.5001", null).andExpect(jsonPath("$.withinSpec", is(false)));
    }

    @Test
    void rascunhoNaoJulgaMedicao() throws Exception {
        var session = login();
        var plan = createPlan(session);
        addPoint(session, plan, "pH do mosto", "4.5", "5.5", "pH", "MAJOR", false);
        var point = firstPoint(session, plan);

        measure(session, plan, point, "5.0", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("plan_not_published")));
    }

    @Test
    void osDesviosAparecemNaListaDosMaisSeverosPrimeiro() throws Exception {
        var session = login();
        var plan = publishedPlan(session, false);
        var point = firstPoint(session, plan);
        measure(session, plan, point, "6.2", null).andExpect(status().isCreated());

        mockMvc.perform(get(DEVIATIONS).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status", is("OPEN")))
                .andExpect(jsonPath("$[0].description", notNullValue()));
    }

    @Test
    void aMedicaoGuardaAVersaoDoPlanoPelaQualFoiJulgada() throws Exception {
        var session = login();
        var plan = publishedPlan(session, false);
        var point = firstPoint(session, plan);
        measure(session, plan, point, "5.0", null).andExpect(status().isCreated());

        mockMvc.perform(get(PLANS + "/" + plan + "/measurements").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].planVersion", is(1)));
    }

    // --- ponto crítico e instrumento (fecha MTR-001-A) ---

    @Test
    void pontoCriticoRecusaInstrumentoVencido() throws Exception {
        var session = login();
        var plan = publishedPlan(session, true);
        var point = firstPoint(session, plan);
        var instrument = expiredInstrument(session);

        mockMvc.perform(post(MEASUREMENTS).session(session).with(csrf()).contentType("application/json")
                        .content("""
                                {"planId":"%s","pointId":"%s","instrumentId":"%s","value":5.0}
                                """.formatted(plan, point, instrument)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("instrument_not_fit")))
                .andExpect(jsonPath("$.controlPoint.fitness", is("EXPIRED")));
    }

    @Test
    void pontoCriticoExigeInstrumentoDeclarado() throws Exception {
        var session = login();
        var plan = publishedPlan(session, true);
        var point = firstPoint(session, plan);

        measure(session, plan, point, "5.0", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.controlPoint.fitness", is("SEM_INSTRUMENTO")));
    }

    @Test
    void pontoNaoCriticoAceitaInstrumentoVencidoMasRegistraAAptidao() throws Exception {
        var session = login();
        var plan = publishedPlan(session, false);
        var point = firstPoint(session, plan);
        var instrument = expiredInstrument(session);

        measure(session, plan, point, "5.0", instrument).andExpect(status().isCreated());

        mockMvc.perform(get(PLANS + "/" + plan + "/measurements").session(session))
                .andExpect(jsonPath("$[0].instrumentFitness", is("EXPIRED")))
                .andExpect(jsonPath("$[0].instrumentQuestionable", is(true)));
    }

    // --- autorização e tenant ---

    @Test
    void recusaSemPermissao() throws Exception {
        var brewery = UUID.randomUUID();

        mockMvc.perform(get(PLANS).with(authentication(principal(brewery, Set.of()))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(PLANS).with(csrf())
                        .with(authentication(principal(brewery, Set.of("quality.plan.read"))))
                        .contentType("application/json")
                        .content("{\"code\":\"PC-X\",\"name\":\"X\",\"stage\":\"BREWING\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void naoEnxergaPlanoDeOutraCervejaria() throws Exception {
        var session = login();
        var plan = createPlan(session);
        var outra = UUID.randomUUID();

        mockMvc.perform(get(PLANS + "/" + plan)
                        .with(authentication(principal(outra, Set.of("quality.plan.read")))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get(PLANS).with(authentication(principal(outra, Set.of("quality.plan.read")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(0)));
    }

    // --- helpers ---

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static void assertDifferentId(String body, String plan) throws Exception {
        var newId = JSON.readTree(body).get("id").asText();
        if (newId.equals(plan)) {
            throw new AssertionError("a nova versão deveria ser outro registro");
        }
    }

    private String createPlan(MockHttpSession session) throws Exception {
        var body = mockMvc.perform(post(PLANS).session(session).with(csrf()).contentType("application/json")
                        .content("""
                                {"code":"PC-%s","name":"Controle de mosto","recipeId":null,"stage":"BREWING"}
                                """.formatted(suffix())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("id").asText();
    }

    private ResultActions addPoint(MockHttpSession session, String plan, String parameter, String min,
            String max, String unit, String severity, boolean critical) throws Exception {
        return mockMvc.perform(post(PLANS + "/" + plan + "/points").session(session).with(csrf())
                .contentType("application/json")
                .content("""
                        {"parameter":"%s","min":%s,"max":%s,"target":null,"unit":"%s",
                         "frequencyKind":"PER_BATCH","everyHours":null,"action":"Ajustar e remedir",
                         "severity":"%s","critical":%s}
                        """.formatted(parameter, min, max, unit, severity, critical)));
    }

    /** Plano publicado com um ponto de pH 4,5–5,5, crítico ou não. */
    private String publishedPlan(MockHttpSession session, boolean critical) throws Exception {
        var plan = createPlan(session);
        addPoint(session, plan, "pH do mosto", "4.5", "5.5", "pH", "MAJOR", critical)
                .andExpect(status().isCreated());
        mockMvc.perform(post(PLANS + "/" + plan + "/publish").session(session).with(csrf()))
                .andExpect(status().isOk());
        return plan;
    }

    private String firstPoint(MockHttpSession session, String plan) throws Exception {
        var body = mockMvc.perform(get(PLANS + "/" + plan).session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("points").get(0).get("id").asText();
    }

    private ResultActions measure(MockHttpSession session, String plan, String point, String value,
            String instrumentId) throws Exception {
        var instrument = instrumentId == null ? "null" : "\"" + instrumentId + "\"";
        return mockMvc.perform(post(MEASUREMENTS).session(session).with(csrf())
                .contentType("application/json")
                .content("""
                        {"planId":"%s","pointId":"%s","batchId":null,"instrumentId":%s,"value":%s,
                         "note":null,"measuredAt":null}
                        """.formatted(plan, point, instrument, value)));
    }

    /** Instrumento com certificado antigo e já vencido — apto no cadastro, vencido no tempo. */
    private String expiredInstrument(MockHttpSession session) throws Exception {
        var body = mockMvc.perform(post(INSTRUMENTS).session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"code":"PH-%s","name":"pHmetro","type":"PH_METER","rangeMin":0,
                                 "rangeMax":14,"resolution":0.01,"accuracy":0.02,"unit":"pH",
                                 "location":"Laboratório"}
                                """.formatted(suffix())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var instrument = JSON.readTree(body).get("id").asText();

        var standardBody = mockMvc.perform(post(STANDARDS).session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"code":"PAD-%s","description":"Solução tampão","certificateNumber":"C-1",
                                 "issuer":"Lab","traceability":"RBC","validUntil":"%s"}
                                """.formatted(suffix(), TODAY.plusYears(2))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var standard = JSON.readTree(standardBody).get("id").asText();

        mockMvc.perform(post(INSTRUMENTS + "/" + instrument + "/calibrations").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"standardId":"%s","performedOn":"%s","dueOn":"%s",
                                 "performedBy":"Metrologista","certificateNumber":"CERT-%s",
                                 "result":"APPROVED","maxDeviation":0.01,"restriction":null,"note":null,
                                 "curve":null}
                                """.formatted(standard, TODAY.minusYears(2), TODAY.minusYears(1), suffix())))
                .andExpect(status().isCreated());
        return instrument;
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
