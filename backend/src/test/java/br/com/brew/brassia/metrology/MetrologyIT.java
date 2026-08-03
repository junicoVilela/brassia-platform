package br.com.brew.brassia.metrology;

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

@SpringBootTest
@Testcontainers
class MetrologyIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String INSTRUMENTS = "/api/v1/metrology/instruments";
    private static final String STANDARDS = "/api/v1/metrology/standards";

    private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);

    @Autowired WebApplicationContext context;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    // --- cadastro ---

    @Test
    void registraInstrumentoQueNasceSemCalibracao() throws Exception {
        var session = login();

        var id = createInstrument(session, "TERM-" + suffix());

        mockMvc.perform(get(INSTRUMENTS + "/" + id).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fitness", is("UNCALIBRATED")))
                .andExpect(jsonPath("$.criticalUse", is(false)))
                .andExpect(jsonPath("$.fitForCriticalUse", is(false)))
                .andExpect(jsonPath("$.calibrationDueOn").doesNotExist())
                .andExpect(jsonPath("$.typeLabel", is("Termômetro")));
    }

    @Test
    void recusaFaixaIncoerente() throws Exception {
        var session = login();

        // Resolução de 5 numa faixa de 0 a 2.
        mockMvc.perform(post(INSTRUMENTS).session(session).with(csrf()).contentType("application/json")
                        .content(instrumentBody("TERM-" + suffix(), "0", "2", "5", "0.5")))
                .andExpect(status().isBadRequest());

        // Mínimo maior que o máximo.
        mockMvc.perform(post(INSTRUMENTS).session(session).with(csrf()).contentType("application/json")
                        .content(instrumentBody("TERM-" + suffix(), "110", "10", "0.1", "0.5")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void recusaCodigoDuplicado() throws Exception {
        var session = login();
        var code = "TERM-" + suffix();
        createInstrument(session, code);

        mockMvc.perform(post(INSTRUMENTS).session(session).with(csrf()).contentType("application/json")
                        .content(instrumentBody(code, "-10", "110", "0.1", "0.5")))
                .andExpect(status().isConflict());
    }

    // --- calibração ---

    @Test
    void calibracaoAprovadaTornaOInstrumentoApto() throws Exception {
        var session = login();
        var instrument = createInstrument(session, "TERM-" + suffix());
        var standard = createStandard(session, "PAD-" + suffix(), TODAY.plusYears(2));

        calibrate(session, instrument, standard, TODAY.minusDays(1), TODAY.plusYears(1), "APPROVED", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fitness", is("FIT")))
                .andExpect(jsonPath("$.calibrationDueOn", is(TODAY.plusYears(1).toString())))
                .andExpect(jsonPath("$.lastCalibration.certificateNumber", notNullValue()));
    }

    @Test
    void padraoVencidoNaoCalibra() throws Exception {
        var session = login();
        var instrument = createInstrument(session, "TERM-" + suffix());
        // Padrão que venceu ontem: calibrar hoje contra ele produziria número sem rastreabilidade.
        var standard = createStandard(session, "PAD-" + suffix(), TODAY.minusDays(1));

        calibrate(session, instrument, standard, TODAY, TODAY.plusYears(1), "APPROVED", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("standard_expired")))
                .andExpect(jsonPath("$.standard.validUntil", is(TODAY.minusDays(1).toString())));
    }

    @Test
    void calibracaoReprovadaDerrubaAAptidao() throws Exception {
        var session = login();
        var instrument = createInstrument(session, "TERM-" + suffix());
        var standard = createStandard(session, "PAD-" + suffix(), TODAY.plusYears(2));

        calibrate(session, instrument, standard, TODAY.minusDays(2), TODAY.plusYears(1), "APPROVED", null)
                .andExpect(jsonPath("$.fitness", is("FIT")));

        calibrate(session, instrument, standard, TODAY.minusDays(1), TODAY.plusYears(1), "REJECTED", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fitness", is("REJECTED")));
    }

    @Test
    void aprovadoComRestricaoExigeDescreverARestricao() throws Exception {
        var session = login();
        var instrument = createInstrument(session, "TERM-" + suffix());
        var standard = createStandard(session, "PAD-" + suffix(), TODAY.plusYears(2));

        calibrate(session, instrument, standard, TODAY, TODAY.plusYears(1), "APPROVED_WITH_RESTRICTION", null)
                .andExpect(status().isBadRequest());

        calibrate(session, instrument, standard, TODAY, TODAY.plusYears(1), "APPROVED_WITH_RESTRICTION",
                "faixa útil de 0 a 60 °C")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fitness", is("FIT")))
                .andExpect(jsonPath("$.lastCalibration.restriction", is("faixa útil de 0 a 60 °C")));
    }

    @Test
    void certificadoPermaneceDepoisDeVencer() throws Exception {
        var session = login();
        var instrument = createInstrument(session, "TERM-" + suffix());
        var standard = createStandard(session, "PAD-" + suffix(), TODAY.plusYears(2));

        // Certificado antigo, já vencido: o instrumento fica VENCIDO, mas o documento continua lá.
        calibrate(session, instrument, standard, TODAY.minusYears(2), TODAY.minusYears(1), "APPROVED", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fitness", is("EXPIRED")));

        mockMvc.perform(get(INSTRUMENTS + "/" + instrument + "/calibrations").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].result", is("APPROVED")))
                .andExpect(jsonPath("$[0].dueOn", is(TODAY.minusYears(1).toString())));
    }

    @Test
    void historicoGuardaTodasAsCalibracoesDaMaisRecenteParaAMaisAntiga() throws Exception {
        var session = login();
        var instrument = createInstrument(session, "TERM-" + suffix());
        var standard = createStandard(session, "PAD-" + suffix(), TODAY.plusYears(2));

        calibrate(session, instrument, standard, TODAY.minusDays(10), TODAY.plusYears(1), "APPROVED", null);
        calibrate(session, instrument, standard, TODAY.minusDays(1), TODAY.plusYears(1), "REJECTED", null);

        mockMvc.perform(get(INSTRUMENTS + "/" + instrument + "/calibrations").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(2)))
                .andExpect(jsonPath("$[0].performedOn", is(TODAY.minusDays(1).toString())))
                .andExpect(jsonPath("$[1].performedOn", is(TODAY.minusDays(10).toString())));
    }

    // --- ponto crítico ---

    @Test
    void naoDesignaParaPontoCriticoInstrumentoVencido() throws Exception {
        var session = login();
        var instrument = createInstrument(session, "TERM-" + suffix());
        var standard = createStandard(session, "PAD-" + suffix(), TODAY.plusYears(2));
        calibrate(session, instrument, standard, TODAY.minusYears(2), TODAY.minusYears(1), "APPROVED", null);

        mockMvc.perform(put(INSTRUMENTS + "/" + instrument + "/critical-use").session(session).with(csrf())
                        .contentType("application/json").content("{\"criticalUse\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("instrument_not_fit")))
                .andExpect(jsonPath("$.instrument.fitness", is("EXPIRED")));
    }

    @Test
    void designaPontoCriticoQuandoAptoEDeixaDeServirAoVencer() throws Exception {
        var session = login();
        var instrument = createInstrument(session, "TERM-" + suffix());
        var standard = createStandard(session, "PAD-" + suffix(), TODAY.plusYears(2));
        calibrate(session, instrument, standard, TODAY.minusDays(1), TODAY.plusYears(1), "APPROVED", null);

        mockMvc.perform(put(INSTRUMENTS + "/" + instrument + "/critical-use").session(session).with(csrf())
                        .contentType("application/json").content("{\"criticalUse\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criticalUse", is(true)))
                .andExpect(jsonPath("$.fitForCriticalUse", is(true)));

        // Uma calibração antiga e já vencida substitui a válida: o instrumento continua designado,
        // mas deixa de servir ao ponto crítico — o tempo decide, sem ninguém tocar no cadastro.
        calibrate(session, instrument, standard, TODAY.minusYears(2), TODAY.minusYears(1), "APPROVED", null)
                .andExpect(jsonPath("$.criticalUse", is(true)))
                .andExpect(jsonPath("$.fitForCriticalUse", is(false)))
                .andExpect(jsonPath("$.fitness", is("EXPIRED")));
    }

    @Test
    void removerDesignacaoCriticaEhSemprePermitido() throws Exception {
        var session = login();
        var instrument = createInstrument(session, "TERM-" + suffix());

        mockMvc.perform(put(INSTRUMENTS + "/" + instrument + "/critical-use").session(session).with(csrf())
                        .contentType("application/json").content("{\"criticalUse\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criticalUse", is(false)));
    }

    // --- estados ---

    @Test
    void bloqueioPrecedeOVencimentoNaAptidao() throws Exception {
        var session = login();
        var instrument = createInstrument(session, "TERM-" + suffix());
        var standard = createStandard(session, "PAD-" + suffix(), TODAY.plusYears(2));
        calibrate(session, instrument, standard, TODAY.minusYears(2), TODAY.minusYears(1), "APPROVED", null)
                .andExpect(jsonPath("$.fitness", is("EXPIRED")));

        mockMvc.perform(post(INSTRUMENTS + "/" + instrument + "/block").session(session).with(csrf())
                        .contentType("application/json").content("{\"reason\":\"queda no chão\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fitness", is("BLOCKED")))
                .andExpect(jsonPath("$.blockReason", is("queda no chão")));

        mockMvc.perform(post(INSTRUMENTS + "/" + instrument + "/unblock").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fitness", is("EXPIRED")));
    }

    @Test
    void baixaEhTerminal() throws Exception {
        var session = login();
        var instrument = createInstrument(session, "TERM-" + suffix());

        mockMvc.perform(post(INSTRUMENTS + "/" + instrument + "/retire").session(session).with(csrf())
                        .contentType("application/json").content("{\"reason\":\"substituído\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fitness", is("RETIRED")));

        mockMvc.perform(post(INSTRUMENTS + "/" + instrument + "/block").session(session).with(csrf())
                        .contentType("application/json").content("{\"reason\":\"x\"}"))
                .andExpect(status().isConflict());
    }

    // --- padrões ---

    @Test
    void listaPadraoMarcandoOsVencidos() throws Exception {
        var session = login();
        createStandard(session, "PAD-" + suffix(), TODAY.minusDays(1));

        mockMvc.perform(get(STANDARDS).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.expired == true)]").exists());
    }

    @Test
    void renovaPadraoExigindoValidadePosteriorAEmissao() throws Exception {
        var session = login();
        var standard = createStandard(session, "PAD-" + suffix(), TODAY.plusMonths(1));

        mockMvc.perform(put(STANDARDS + "/" + standard).session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"certificateNumber":"CERT-NOVO","issuer":"Lab Y","validUntil":"%s",
                                 "issuedOn":"%s"}
                                """.formatted(TODAY.toString(), TODAY.toString())))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put(STANDARDS + "/" + standard).session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"certificateNumber":"CERT-NOVO","issuer":"Lab Y","validUntil":"%s",
                                 "issuedOn":"%s"}
                                """.formatted(TODAY.plusYears(2).toString(), TODAY.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.certificateNumber", is("CERT-NOVO")))
                .andExpect(jsonPath("$.expired", is(false)));
    }

    // --- autorização e tenant ---

    @Test
    void recusaSemPermissao() throws Exception {
        var brewery = UUID.randomUUID();

        mockMvc.perform(get(INSTRUMENTS).with(authentication(principal(brewery, Set.of()))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(INSTRUMENTS).with(csrf())
                        .with(authentication(principal(brewery, Set.of("metrology.instrument.read"))))
                        .contentType("application/json")
                        .content(instrumentBody("TERM-X", "-10", "110", "0.1", "0.5")))
                .andExpect(status().isForbidden());
    }

    @Test
    void naoEnxergaInstrumentoDeOutraCervejaria() throws Exception {
        var session = login();
        var instrument = createInstrument(session, "TERM-" + suffix());

        var outra = UUID.randomUUID();
        mockMvc.perform(get(INSTRUMENTS + "/" + instrument)
                        .with(authentication(principal(outra, Set.of("metrology.instrument.read")))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get(INSTRUMENTS)
                        .with(authentication(principal(outra, Set.of("metrology.instrument.read")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(0)));
    }

    // --- helpers ---

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static String instrumentBody(String code, String min, String max, String resolution,
            String accuracy) {
        return """
                {"code":"%s","name":"Termômetro do fermentador","type":"THERMOMETER","rangeMin":%s,
                 "rangeMax":%s,"resolution":%s,"accuracy":%s,"unit":"°C","location":"Sala de fermentação"}
                """.formatted(code, min, max, resolution, accuracy);
    }

    private String createInstrument(MockHttpSession session, String code) throws Exception {
        var body = mockMvc.perform(post(INSTRUMENTS).session(session).with(csrf())
                        .contentType("application/json")
                        .content(instrumentBody(code, "-10", "110", "0.1", "0.5")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("id").asText();
    }

    private String createStandard(MockHttpSession session, String code, LocalDate validUntil) throws Exception {
        var body = mockMvc.perform(post(STANDARDS).session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"code":"%s","description":"Banho térmico de referência",
                                 "certificateNumber":"CERT-9001","issuer":"Laboratório X",
                                 "traceability":"RBC","validUntil":"%s"}
                                """.formatted(code, validUntil)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("id").asText();
    }

    private org.springframework.test.web.servlet.ResultActions calibrate(MockHttpSession session,
            String instrumentId, String standardId, LocalDate performedOn, LocalDate dueOn, String result,
            String restriction) throws Exception {
        var restrictionJson = restriction == null ? "null" : "\"" + restriction + "\"";
        return mockMvc.perform(post(INSTRUMENTS + "/" + instrumentId + "/calibrations").session(session)
                .with(csrf()).contentType("application/json")
                .content("""
                        {"standardId":"%s","performedOn":"%s","dueOn":"%s","performedBy":"Metrologista",
                         "certificateNumber":"CERT-%s","result":"%s","maxDeviation":0.2,
                         "restriction":%s,"note":null}
                        """.formatted(standardId, performedOn, dueOn, suffix(), result, restrictionJson)));
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
