package br.com.brew.brassia.quality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
    private static final String NON_CONFORMITIES = "/api/v1/quality/non-conformities";
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

    /**
     * Tirar um ponto do plano, e a porta que se fecha quando ele é publicado.
     *
     * <p>O endpoint não tinha teste. Ele existe porque montar um plano é trabalho de rascunho, e errar o
     * ponto não pode custar o plano inteiro. Mas <strong>publicar congela a versão</strong>: tirar um
     * ponto de um plano que já julgou medições reescreveria o critério com que elas foram julgadas —
     * e o caminho para mudar um plano publicado é `new-version`, não `delete`.
     */
    @Test
    void oPontoSaiDoRascunhoENaoSaiDoPlanoPublicado() throws Exception {
        var session = login();
        var plan = createPlan(session);
        var corpo = addPoint(session, plan, "Oxigênio dissolvido", "null", "50", "ppb", "MAJOR", false)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var ponto = JSON.readTree(corpo).get("points").get(0).get("id").asText();

        mockMvc.perform(delete(PLANS + "/" + plan + "/points/" + ponto).session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points.length()", is(0)));

        // Sem ponto, o plano volta a não poder publicar — que é a regra do teste vizinho, aqui como efeito.
        mockMvc.perform(post(PLANS + "/" + plan + "/publish").session(session).with(csrf()))
                .andExpect(status().isConflict());

        // Publicado, o ponto não sai mais.
        var publicado = createPlan(session);
        var corpoPub = addPoint(session, publicado, "pH", "4.0", "4.6", "pH", "MAJOR", false)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var pontoPub = JSON.readTree(corpoPub).get("points").get(0).get("id").asText();
        mockMvc.perform(post(PLANS + "/" + publicado + "/publish").session(session).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(delete(PLANS + "/" + publicado + "/points/" + pontoPub).session(session)
                        .with(csrf()))
                .andExpect(status().isConflict());
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

    // --- não conformidade e CAPA (QLT-002) ---

    @Test
    void asFasesTemOrdem() throws Exception {
        var session = login();
        var nc = openNonConformity(session, null, "OTHER");

        // Investigar sem conter.
        mockMvc.perform(post(NON_CONFORMITIES + "/" + nc + "/investigation").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"rootCause\":\"causa\",\"method\":\"5 porquês\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("nc_phase_out_of_order")))
                .andExpect(jsonPath("$.nonConformity.status", is("OPEN")));

        contain(session, nc).andExpect(status().isOk()).andExpect(jsonPath("$.status", is("CONTAINED")));

        // Agir sem causa raiz.
        planAction(session, nc, "CORRECTIVE").andExpect(status().isConflict());
    }

    @Test
    void encerrarExigeVerificacaoEficaz() throws Exception {
        var session = login();
        var nc = openNonConformity(session, null, "OTHER");
        var action = untilActionCompleted(session, nc);

        // Sem verificação nenhuma.
        mockMvc.perform(post(NON_CONFORMITIES + "/" + nc + "/close").session(session).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("verification_required")));

        verify(session, nc, true).andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("VERIFIED")))
                .andExpect(jsonPath("$.closable", is(true)));

        mockMvc.perform(post(NON_CONFORMITIES + "/" + nc + "/close").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CLOSED")));
        assertThat(action).isNotBlank();
    }

    @Test
    void verificacaoIneficazDevolveAFaseDeAcaoENaoEncerra() throws Exception {
        var session = login();
        var nc = openNonConformity(session, null, "OTHER");
        untilActionCompleted(session, nc);

        verify(session, nc, false).andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("INVESTIGATED")))
                .andExpect(jsonPath("$.closable", is(false)))
                .andExpect(jsonPath("$.verifications.length()", is(1)))
                .andExpect(jsonPath("$.verifications[0].effective", is(false)));

        mockMvc.perform(post(NON_CONFORMITIES + "/" + nc + "/close").session(session).with(csrf()))
                .andExpect(status().isConflict());

        // E aceita ação nova, que é o que a verificação negativa exige.
        planAction(session, nc, "PREVENTIVE").andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("ACTION_PLANNED")))
                .andExpect(jsonPath("$.actions.length()", is(2)));
    }

    @Test
    void naoVerificaEficaciaSemConcluirAcao() throws Exception {
        var session = login();
        var nc = openNonConformity(session, null, "OTHER");
        contain(session, nc);
        investigate(session, nc);
        planAction(session, nc, "CORRECTIVE");

        verify(session, nc, true)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("verification_required")));
    }

    @Test
    void encerrarANaoConformidadeFechaODesvioDeOrigem() throws Exception {
        var session = login();
        var plan = publishedPlan(session, false);
        var point = firstPoint(session, plan);
        var body = measure(session, plan, point, "6.2", null)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var deviation = JSON.readTree(body).get("deviationId").asText();

        // O desvio está aberto e aparece na lista.
        mockMvc.perform(get(DEVIATIONS).session(session))
                .andExpect(jsonPath("$[?(@.id == '" + deviation + "')]").exists());

        var nc = openNonConformity(session, deviation, "DEVIATION");
        untilActionCompleted(session, nc);
        verify(session, nc, true);
        mockMvc.perform(post(NON_CONFORMITIES + "/" + nc + "/close").session(session).with(csrf()))
                .andExpect(status().isOk());

        // Encerrada a NC, o desvio sai da lista de abertos: o ciclo da QLT-001 se fecha.
        mockMvc.perform(get(DEVIATIONS).session(session))
                .andExpect(jsonPath("$[?(@.id == '" + deviation + "')]").doesNotExist());
    }

    @Test
    void origemDesvioExigeDesvioExistente() throws Exception {
        var session = login();

        mockMvc.perform(post(NON_CONFORMITIES).session(session).with(csrf())
                        .contentType("application/json")
                        .content(ncBody("NC-" + suffix(), UUID.randomUUID().toString(), "DEVIATION")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void oPrazoVencidoVemDerivadoNaConsulta() throws Exception {
        var session = login();
        // Prazo de contenção já vencido: a NC nasce atrasada, sem ninguém ter marcado nada.
        var body = mockMvc.perform(post(NON_CONFORMITIES).session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"code":"NC-%s","title":"Atrasada","description":"d","source":"AUDIT",
                                 "deviationId":null,"severity":"MAJOR","containmentDueOn":"%s",
                                 "investigationDueOn":"%s","verificationDueOn":"%s"}
                                """.formatted(suffix(), TODAY.minusDays(3), TODAY.minusDays(2),
                                TODAY.minusDays(1))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var nc = JSON.readTree(body).get("id").asText();

        mockMvc.perform(get(NON_CONFORMITIES + "/" + nc).session(session))
                .andExpect(jsonPath("$.overdue", is(true)))
                .andExpect(jsonPath("$.overduePhases", hasItem("containment")));
    }

    @Test
    void encerrarEhAlcadaPropria() throws Exception {
        var brewery = UUID.randomUUID();

        mockMvc.perform(post(NON_CONFORMITIES + "/" + UUID.randomUUID() + "/close").with(csrf())
                        .with(authentication(principal(brewery,
                                Set.of("quality.nc.read", "quality.nc.manage")))))
                .andExpect(status().isForbidden());
    }

    @Test
    void naoEnxergaNaoConformidadeDeOutraCervejaria() throws Exception {
        var session = login();
        var nc = openNonConformity(session, null, "OTHER");

        mockMvc.perform(get(NON_CONFORMITIES + "/" + nc)
                        .with(authentication(principal(UUID.randomUUID(), Set.of("quality.nc.read")))))
                .andExpect(status().isBadRequest());
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

    // --- helpers de não conformidade ---

    private static String ncBody(String code, String deviationId, String source) {
        var deviation = deviationId == null ? "null" : "\"" + deviationId + "\"";
        return """
                {"code":"%s","title":"pH fora da faixa","description":"Medição acusou 6,2","source":"%s",
                 "deviationId":%s,"severity":"MAJOR","containmentDueOn":"%s","investigationDueOn":"%s",
                 "verificationDueOn":"%s"}
                """.formatted(code, source, deviation, TODAY.plusDays(1), TODAY.plusDays(5),
                TODAY.plusDays(30));
    }

    private String openNonConformity(MockHttpSession session, String deviationId, String source)
            throws Exception {
        var body = mockMvc.perform(post(NON_CONFORMITIES).session(session).with(csrf())
                        .contentType("application/json")
                        .content(ncBody("NC-" + suffix(), deviationId, source)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("id").asText();
    }

    private ResultActions contain(MockHttpSession session, String nc) throws Exception {
        return mockMvc.perform(post(NON_CONFORMITIES + "/" + nc + "/containment").session(session)
                .with(csrf()).contentType("application/json")
                .content("{\"description\":\"Lote segregado no FV-03\"}"));
    }

    private ResultActions investigate(MockHttpSession session, String nc) throws Exception {
        return mockMvc.perform(post(NON_CONFORMITIES + "/" + nc + "/investigation").session(session)
                .with(csrf()).contentType("application/json")
                .content("{\"rootCause\":\"Água com alcalinidade alta\",\"method\":\"5 porquês\"}"));
    }

    private ResultActions planAction(MockHttpSession session, String nc, String kind) throws Exception {
        return mockMvc.perform(post(NON_CONFORMITIES + "/" + nc + "/actions").session(session).with(csrf())
                .contentType("application/json")
                .content("""
                        {"kind":"%s","description":"Ajustar o pH","owner":"Brassista","dueOn":"%s"}
                        """.formatted(kind, TODAY.plusDays(2))));
    }

    private ResultActions verify(MockHttpSession session, String nc, boolean effective) throws Exception {
        return mockMvc.perform(post(NON_CONFORMITIES + "/" + nc + "/verification").session(session)
                .with(csrf()).contentType("application/json")
                .content("""
                        {"effective":%s,"evidence":"Três lotes seguintes dentro da faixa"}
                        """.formatted(effective)));
    }

    /** Leva a NC até ter uma ação concluída — o estado mínimo para verificar eficácia. */
    private String untilActionCompleted(MockHttpSession session, String nc) throws Exception {
        contain(session, nc).andExpect(status().isOk());
        investigate(session, nc).andExpect(status().isOk());
        var body = planAction(session, nc, "CORRECTIVE").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var action = JSON.readTree(body).get("actions").get(0).get("id").asText();
        mockMvc.perform(post(NON_CONFORMITIES + "/" + nc + "/actions/" + action + "/complete")
                        .session(session).with(csrf()))
                .andExpect(status().isOk());
        return action;
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
