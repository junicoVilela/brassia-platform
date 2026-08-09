package br.com.brew.brassia.sensor;

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
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
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
 * Ingestão de sensor de ponta a ponta (INT-001).
 *
 * <p>Aqui está o que nenhum teste de unidade cobre: <strong>a restrição única que decide a idempotência</strong>.
 * O dublê do teste de unidade imita o comportamento dela e por isso não pode prová-lo — em particular, não
 * pode provar que ela resolve o reenvio <em>concorrente</em>, que é o caso para o qual ela existe. O teste
 * de concorrência abaixo dispara oito requisições simultâneas com a mesma identidade de mensagem contra
 * PostgreSQL de verdade.
 */
@SpringBootTest
@Testcontainers
class SensorIngestionIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SENSORS = "/api/v1/sensors";

    /**
     * Instante de medição plausível: um pouco antes de agora.
     *
     * <p>Deliberadamente relativo e não uma constante fixa. Uma data fixa passa a ser "futuro" quando o
     * relógio da máquina está atrás dela, e o domínio — corretamente — sinalizaria toda leitura como
     * {@code FUTURE_CLOCK}, fazendo o teste falhar por um motivo que não tem nada a ver com o que ele
     * afirma. O relógio adiantado é exercitado no seu próprio teste, de propósito.
     */
    private static Instant mediu() {
        return Instant.now().minus(Duration.ofSeconds(30));
    }

    @Autowired WebApplicationContext context;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("cadastra dispositivo e recebe a primeira leitura com 201")
    void cadastraERecebe() throws Exception {
        var session = login();
        var code = uniqueCode("TANK");
        registerDevice(session, code, "TEMPERATURE", "C", 300);

        ingest(session, code, "msg-1", "TEMPERATURE", "18.5", "C", mediu())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.duplicate").value(false))
                .andExpect(jsonPath("$.reading.quality").value("GOOD"))
                .andExpect(jsonPath("$.reading.unit").value("C"));
    }

    @Test
    @DisplayName("reenvio da mesma mensagem responde 200, não cria segunda leitura e devolve a original")
    void reenvioEIdempotente() throws Exception {
        // O critério da história. O 200 diz "está registrado, pode parar" — um erro ensinaria o
        // dispositivo a continuar tentando.
        var session = login();
        var code = uniqueCode("TANK");
        var device = registerDevice(session, code, "TEMPERATURE", "C", 300);

        var primeira = read(ingest(session, code, "msg-dup", "TEMPERATURE", "18.5", "C", mediu())
                .andExpect(status().isCreated()));

        var segunda = read(ingest(session, code, "msg-dup", "TEMPERATURE", "18.5", "C", mediu())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true)));

        // Mesma linha: o id da leitura devolvida é o da primeira.
        assertThat(segunda.get("reading").get("id").asText())
                .isEqualTo(primeira.get("reading").get("id").asText());

        var readings = readingsOf(session, device);
        assertThat(readings).hasSize(1);
    }

    @Test
    @DisplayName("oito reenvios SIMULTÂNEOS da mesma mensagem gravam uma leitura só")
    void reenvioConcorrenteGravaUmaSo() throws Exception {
        // Este é o teste que justifica a restrição única existir em vez de uma consulta prévia: o caminho
        // "procura se já existe, senão insere" tem uma janela entre a pergunta e a escrita, e é dentro
        // dela que caem estas oito requisições.
        var session = login();
        var code = uniqueCode("TANK");
        var device = registerDevice(session, code, "TEMPERATURE", "C", 300);
        var brewery = breweryOf(session);
        var principal = principal(brewery, Set.of("sensor.reading.ingest", "sensor.reading.read"));

        Callable<Integer> envio = () -> mockMvc.perform(post(SENSORS + "/readings").with(csrf())
                        .with(authentication(principal))
                        .contentType("application/json")
                        .content(ingestBody(code, "msg-race", "TEMPERATURE", "18.5", "C", mediu())))
                .andReturn().getResponse().getStatus();

        try (var pool = Executors.newFixedThreadPool(8)) {
            var futures = pool.invokeAll(IntStream.range(0, 8).mapToObj(i -> envio).toList());
            var statuses = futures.stream().map(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }).toList();

            // Exatamente uma criou; as outras sete reconheceram a repetição. Nenhuma falhou.
            assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
            assertThat(statuses).filteredOn(s -> s == 200).hasSize(7);
        }

        assertThat(readingsOf(session, device)).hasSize(1);
    }

    @Test
    @DisplayName("valor fora da faixa é GRAVADO e sinalizado com motivo, não recusado")
    void foraDaFaixaEGravadoESinalizado() throws Exception {
        // Recusar deixaria um buraco na curva, indistinguível de "o sensor não mediu".
        var session = login();
        var code = uniqueCode("TANK");
        var device = registerDevice(session, code, "TEMPERATURE", "C", 300);

        ingest(session, code, "msg-hot", "TEMPERATURE", "85", "C", mediu())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reading.quality").value("OUT_OF_RANGE"))
                .andExpect(jsonPath("$.reading.qualityReason").isNotEmpty());

        assertThat(readingsOf(session, device)).hasSize(1);
    }

    @Test
    @DisplayName("medição datada no futuro é FUTURE_CLOCK e o atraso negativo é preservado")
    void relogioAdiantadoESinalizado() throws Exception {
        var session = login();
        var code = uniqueCode("TANK");
        registerDevice(session, code, "TEMPERATURE", "C", 300);

        var futuro = Instant.now().plus(Duration.ofHours(2));
        var body = read(ingest(session, code, "msg-future", "TEMPERATURE", "18.5", "C", futuro)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reading.quality").value("FUTURE_CLOCK")));

        assertThat(body.get("reading").get("delaySeconds").asLong()).isNegative();
        assertThat(body.get("reading").get("late").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("leitura atrasada além do intervalo é sinalizada sem perder a qualidade do valor")
    void atrasoESinalizadoSemAfetarQualidade() throws Exception {
        // Gateway sem rede por meia hora: valor perfeito, chegada atrasada. Os dois eixos são independentes.
        var session = login();
        var code = uniqueCode("TANK");
        registerDevice(session, code, "TEMPERATURE", "C", 300);

        var antiga = Instant.now().minus(Duration.ofMinutes(30));
        ingest(session, code, "msg-late", "TEMPERATURE", "18.5", "C", antiga)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reading.quality").value("GOOD"))
                .andExpect(jsonPath("$.reading.late").value(true));
    }

    @Test
    @DisplayName("a janela de consulta é por instante de MEDIÇÃO, não de chegada")
    void janelaEPorInstanteDeMedicao() throws Exception {
        // Quem pergunta "o que houve entre 8h e 12h" quer os fatos daquele intervalo. Uma leitura das 9h
        // represada e entregue às 14h pertence à janela da manhã.
        var session = login();
        var code = uniqueCode("TANK");
        var device = registerDevice(session, code, "TEMPERATURE", "C", 300);

        var antiga = Instant.now().minus(Duration.ofHours(6));
        ingest(session, code, "msg-old", "TEMPERATURE", "18.5", "C", antiga)
                .andExpect(status().isCreated());

        // Janela que cobre a medição: aparece.
        var dentro = read(mockMvc.perform(get(SENSORS + "/devices/" + device + "/readings")
                        .param("from", antiga.minus(Duration.ofMinutes(10)).toString())
                        .param("to", antiga.plus(Duration.ofMinutes(10)).toString())
                        .session(session))
                .andExpect(status().isOk()));
        assertThat(dentro).hasSize(1);

        // Janela recente, que cobre a CHEGADA mas não a medição: não aparece.
        var fora = read(mockMvc.perform(get(SENSORS + "/devices/" + device + "/readings")
                        .param("from", Instant.now().minus(Duration.ofMinutes(5)).toString())
                        .param("to", Instant.now().plus(Duration.ofMinutes(5)).toString())
                        .session(session))
                .andExpect(status().isOk()));
        assertThat(fora).isEmpty();
    }

    @Test
    @DisplayName("dispositivo pausado recusa leitura com 409 e o estado na resposta")
    void pausadoRecusaCom409() throws Exception {
        var session = login();
        var code = uniqueCode("TANK");
        var device = registerDevice(session, code, "TEMPERATURE", "C", 300);

        changeStatus(session, device, "PAUSED", 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"));

        ingest(session, code, "msg-1", "TEMPERATURE", "18.5", "C", mediu())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("sensor_device_inactive"))
                .andExpect(jsonPath("$.status").value("PAUSED"));
    }

    @Test
    @DisplayName("revogado é terminal: não volta a ACTIVE")
    void revogadoNaoVolta() throws Exception {
        var session = login();
        var code = uniqueCode("TANK");
        var device = registerDevice(session, code, "TEMPERATURE", "C", 300);

        changeStatus(session, device, "REVOKED", 0).andExpect(status().isOk());
        changeStatus(session, device, "ACTIVE", 1).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("versão desatualizada perde: dois operadores não decidem o mesmo dispositivo em silêncio")
    void concorrenciaOtimistaNoDispositivo() throws Exception {
        var session = login();
        var code = uniqueCode("TANK");
        var device = registerDevice(session, code, "TEMPERATURE", "C", 300);

        changeStatus(session, device, "PAUSED", 0).andExpect(status().isOk());
        // Segundo operador ainda tinha a versão 0 na tela.
        changeStatus(session, device, "REVOKED", 0).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("revogar é alçada própria: quem só administra não descontinua uma série")
    void revogarExigeAlcadaPropria() throws Exception {
        var session = login();
        var code = uniqueCode("TANK");
        var device = registerDevice(session, code, "TEMPERATURE", "C", 300);
        var brewery = breweryOf(session);

        var manager = principal(brewery, Set.of("sensor.device.manage", "sensor.reading.read"));

        // Pausar pode.
        mockMvc.perform(post(SENSORS + "/devices/" + device + "/status").with(csrf())
                        .with(authentication(manager)).contentType("application/json")
                        .content("{\"status\":\"PAUSED\",\"expectedVersion\":0}"))
                .andExpect(status().isOk());

        // Revogar não.
        mockMvc.perform(post(SENSORS + "/devices/" + device + "/status").with(csrf())
                        .with(authentication(manager)).contentType("application/json")
                        .content("{\"status\":\"REVOKED\",\"expectedVersion\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("dispositivo de outra cervejaria é 404, não lista vazia")
    void isolaPorCervejaria() throws Exception {
        var session = login();
        var code = uniqueCode("TANK");
        var device = registerDevice(session, code, "TEMPERATURE", "C", 300);

        var outra = principal(UUID.randomUUID(),
                Set.of("sensor.reading.ingest", "sensor.reading.read", "sensor.device.manage"));

        // A leitura para o mesmo código não acha o dispositivo.
        mockMvc.perform(post(SENSORS + "/readings").with(csrf()).with(authentication(outra))
                        .contentType("application/json")
                        .content(ingestBody(code, "msg-1", "TEMPERATURE", "18.5", "C", mediu())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("unknown_sensor_device"));

        // E a consulta pelo id do dispositivo também não.
        mockMvc.perform(get(SENSORS + "/devices/" + device + "/readings").with(authentication(outra)))
                .andExpect(status().isNotFound());

        // A listagem da outra cervejaria não mostra este dispositivo.
        var listed = read(mockMvc.perform(get(SENSORS + "/devices").with(authentication(outra)))
                .andExpect(status().isOk()));
        assertThat(listed.findValuesAsText("code")).doesNotContain(code);
    }

    @Test
    @DisplayName("grandeza e unidade divergentes do cadastro são recusadas, não convertidas")
    void divergenciaDoCadastroERecusada() throws Exception {
        var session = login();
        var code = uniqueCode("TANK");
        registerDevice(session, code, "TEMPERATURE", "C", 300);

        // Firmware que passou a mandar Fahrenheit: converter trocaria a escala da série inteira em silêncio.
        ingest(session, code, "msg-f", "TEMPERATURE", "65", "F", mediu())
                .andExpect(status().isBadRequest());

        ingest(session, code, "msg-p", "PRESSURE", "12", "PSI", mediu())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("ingerir é alçada do dispositivo: quem só lê não injeta leitura")
    void ingerirExigeAlcadaPropria() throws Exception {
        var reader = principal(UUID.randomUUID(), Set.of("sensor.reading.read"));

        mockMvc.perform(get(SENSORS + "/devices").with(authentication(reader)))
                .andExpect(status().isOk());
        mockMvc.perform(post(SENSORS + "/readings").with(csrf()).with(authentication(reader))
                        .contentType("application/json")
                        .content(ingestBody("TANK-X", "msg-1", "TEMPERATURE", "18.5", "C", mediu())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("sem permissão nenhuma, nada responde")
    void semPermissaoNadaResponde() throws Exception {
        var nobody = principal(UUID.randomUUID(), Set.of());

        mockMvc.perform(get(SENSORS + "/devices").with(authentication(nobody)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("código duplicado no cadastro é conflito, não segundo dispositivo")
    void codigoDuplicadoEConflito() throws Exception {
        var session = login();
        var code = uniqueCode("TANK");
        registerDevice(session, code, "TEMPERATURE", "C", 300);

        mockMvc.perform(post(SENSORS + "/devices").with(csrf()).session(session)
                        .contentType("application/json")
                        .content(deviceBody(code, "TEMPERATURE", "C", 300)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("mensagem sem identificador é recusada no contrato")
    void mensagemSemIdentificadorERecusada() throws Exception {
        var session = login();
        var code = uniqueCode("TANK");
        registerDevice(session, code, "TEMPERATURE", "C", 300);

        mockMvc.perform(post(SENSORS + "/readings").with(csrf()).session(session)
                        .contentType("application/json")
                        .content(ingestBody(code, "  ", "TEMPERATURE", "18.5", "C", mediu())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    @Test
    @DisplayName("cadastrar dispositivo é auditado")
    void cadastroEAuditado() throws Exception {
        var session = login();
        var code = uniqueCode("TANK");
        registerDevice(session, code, "TEMPERATURE", "C", 300);

        var audit = read(mockMvc.perform(get("/api/v1/security/audit-events")
                        .param("action", "sensor.device.register").session(session))
                .andExpect(status().isOk()));

        assertThat(audit.toString()).contains("sensor.device.register");
    }

    @Test
    @DisplayName("INT-006: payload de iSpindel é traduzido e vira leitura da grandeza cadastrada")
    void adapterIspindel() throws Exception {
        var session = login();
        var code = uniqueCode("SPINDEL");
        var device = registerDeviceWithFormat(session, code, "DENSITY", "SG", "ISPINDEL");

        mockMvc.perform(post(SENSORS + "/devices/" + code + "/payload").with(csrf()).session(session)
                        .contentType("application/json")
                        .content("{\"name\":\"" + code + "\",\"ID\":\"4242\","
                                + "\"temperature\":18.5,\"gravity\":1.048}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.readings[0].reading.measure").value("DENSITY"))
                .andExpect(jsonPath("$.readings[0].reading.unit").value("SG"))
                .andExpect(jsonPath("$.readings[0].duplicate").value(false));

        assertThat(readingsOf(session, device)).hasSize(1);
    }

    @Test
    @DisplayName("INT-006: a idempotência sobrevive ao adapter — chave derivada, não sorteada")
    void adapterMantemIdempotencia() throws Exception {
        // Se a chave fosse sorteada por leitura, o adapter seria o furo por onde a idempotência de INT-001
        // vaza: cada reenvio criaria uma leitura nova.
        var session = login();
        var code = uniqueCode("TANK");
        var device = registerDeviceWithFormat(session, code, "TEMPERATURE", "C", "CANONICAL");

        var body = "{\"deviceId\":\"" + code + "\",\"externalReadingId\":\"msg-adapter\","
                + "\"measuredAt\":\"" + mediu() + "\",\"temperatureC\":18.5}";

        mockMvc.perform(post(SENSORS + "/devices/" + code + "/payload").with(csrf()).session(session)
                        .contentType("application/json").content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.readings[0].duplicate").value(false));

        mockMvc.perform(post(SENSORS + "/devices/" + code + "/payload").with(csrf()).session(session)
                        .contentType("application/json").content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.readings[0].duplicate").value(true));

        assertThat(readingsOf(session, device)).hasSize(1);
    }

    @Test
    @DisplayName("INT-006: mensagem sem a grandeza cadastrada é recusada, não gravada em branco")
    void adapterExigeAGrandezaCadastrada() throws Exception {
        // Um dispositivo cadastrado como termômetro não começa a gravar densidade porque o firmware passou
        // a incluí-la — a série mudaria de assunto sozinha.
        var session = login();
        var code = uniqueCode("TANK");
        registerDeviceWithFormat(session, code, "TEMPERATURE", "C", "ISPINDEL");

        mockMvc.perform(post(SENSORS + "/devices/" + code + "/payload").with(csrf()).session(session)
                        .contentType("application/json")
                        .content("{\"name\":\"" + code + "\",\"gravity\":1.048}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("INT-006: o dispositivo vem da URL, não do que o payload diz ser")
    void adapterResolveDispositivoPelaUrl() throws Exception {
        // Deixar o payload escolher permitiria a um gateway gravar na série de outro aparelho da mesma
        // cervejaria.
        var session = login();
        var code = uniqueCode("TANK");
        var device = registerDeviceWithFormat(session, code, "TEMPERATURE", "C", "CANONICAL");

        mockMvc.perform(post(SENSORS + "/devices/" + code + "/payload").with(csrf()).session(session)
                        .contentType("application/json")
                        .content("{\"deviceId\":\"OUTRO-APARELHO\",\"externalReadingId\":\"m-1\","
                                + "\"measuredAt\":\"" + mediu() + "\",\"temperatureC\":18.5}"))
                .andExpect(status().isAccepted());

        // Gravou na série do dispositivo da URL.
        assertThat(readingsOf(session, device)).hasSize(1);
    }

    @Test
    @DisplayName("INT-006: dispositivo desconhecido no adapter é 404")
    void adapterDispositivoDesconhecido() throws Exception {
        var session = login();

        mockMvc.perform(post(SENSORS + "/devices/NAO-EXISTE/payload").with(csrf()).session(session)
                        .contentType("application/json")
                        .content("{\"deviceId\":\"x\",\"externalReadingId\":\"m-1\","
                                + "\"measuredAt\":\"" + mediu() + "\",\"temperatureC\":18.5}"))
                .andExpect(status().isNotFound());
    }

    // --- infraestrutura ---

    private UUID registerDeviceWithFormat(MockHttpSession session, String code, String measure, String unit,
            String format) throws Exception {
        var node = JSON.createObjectNode();
        node.put("code", code);
        node.put("name", "Dispositivo " + code);
        node.put("measure", measure);
        node.put("unit", unit);
        node.put("payloadFormat", format);
        var body = read(mockMvc.perform(post(SENSORS + "/devices").with(csrf()).session(session)
                        .contentType("application/json").content(JSON.writeValueAsString(node)))
                .andExpect(status().isCreated()));
        return UUID.fromString(body.get("id").asText());
    }


    private UUID registerDevice(MockHttpSession session, String code, String measure, String unit,
            Integer interval) throws Exception {
        var body = read(mockMvc.perform(post(SENSORS + "/devices").with(csrf()).session(session)
                        .contentType("application/json")
                        .content(deviceBody(code, measure, unit, interval)))
                .andExpect(status().isCreated()));
        return UUID.fromString(body.get("id").asText());
    }

    private static String deviceBody(String code, String measure, String unit, Integer interval)
            throws Exception {
        var node = JSON.createObjectNode();
        node.put("code", code);
        node.put("name", "Dispositivo " + code);
        node.put("measure", measure);
        node.put("unit", unit);
        if (interval != null) {
            node.put("expectedIntervalSeconds", interval);
        }
        return JSON.writeValueAsString(node);
    }

    private ResultActions ingest(MockHttpSession session, String code, String messageId, String measure,
            String value, String unit, Instant measuredAt) throws Exception {
        return mockMvc.perform(post(SENSORS + "/readings").with(csrf()).session(session)
                .contentType("application/json")
                .content(ingestBody(code, messageId, measure, value, unit, measuredAt)));
    }

    private static String ingestBody(String code, String messageId, String measure, String value,
            String unit, Instant measuredAt) throws Exception {
        var node = JSON.createObjectNode();
        node.put("deviceCode", code);
        node.put("messageId", messageId);
        node.put("measure", measure);
        node.put("value", new java.math.BigDecimal(value));
        node.put("unit", unit);
        node.put("measuredAt", measuredAt.toString());
        return JSON.writeValueAsString(node);
    }

    private ResultActions changeStatus(MockHttpSession session, UUID device, String status, long version)
            throws Exception {
        return mockMvc.perform(post(SENSORS + "/devices/" + device + "/status").with(csrf())
                .session(session).contentType("application/json")
                .content("{\"status\":\"" + status + "\",\"expectedVersion\":" + version + "}"));
    }

    private JsonNode readingsOf(MockHttpSession session, UUID device) throws Exception {
        return read(mockMvc.perform(get(SENSORS + "/devices/" + device + "/readings")
                        .param("from", Instant.parse("2020-01-01T00:00:00Z").toString())
                        .param("to", Instant.now().plus(Duration.ofDays(3650)).toString())
                        .session(session))
                .andExpect(status().isOk()));
    }

    /** Código único por teste: os testes compartilham o banco, e código é chave do dispositivo. */
    private static String uniqueCode(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT);
    }

    private UUID breweryOf(MockHttpSession session) throws Exception {
        var body = read(mockMvc.perform(get("/api/v1/security/session").session(session))
                .andExpect(status().isOk()));
        return UUID.fromString(body.get("activeBrewery").get("id").asText());
    }

    private JsonNode read(ResultActions actions) throws Exception {
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
