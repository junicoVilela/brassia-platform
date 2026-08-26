package br.com.brew.brassia.distribution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import br.com.brew.brassia.container.ContainerShippingLookup;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Sincronização do aplicativo de distribuição de ponta a ponta (MOB-001).
 *
 * <p>O que estes testes fixam: <strong>o reenvio devolve o mesmo resultado</strong>, o conflito não se
 * resolve sozinho, e um item recusado não derruba os outros do lote.
 */
@SpringBootTest
@Testcontainers
@Import(LoadIT.ScriptedContainers.class)
class SyncIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String BASE = "/api/v1/distribution/loads";
    private static final String SYNC = "/api/v1/distribution/sync";

    @Autowired
    WebApplicationContext context;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    LoadIT.Vasilhames vasilhames;

    @Autowired
    LoadIT.Movimentos movimentos;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void aFilaAcumuladaOfflineEntraEACadaItemSeuDesfecho() throws Exception {
        // "Sincronizado" sozinho não distingue o que entrou do que foi recusado.
        var session = login();
        var cena = cenaNaRua(session);
        var aparelho = UUID.randomUUID();

        var corpo = sincroniza(session, aparelho,
                operacao(UUID.randomUUID(), 1, cena, "DELIVERED", cena.keg()));

        assertThat(JSON.readTree(corpo).get(0).get("status").asText()).isEqualTo("APPLIED");
        assertThat(JSON.readTree(corpo).get(0).get("resultId").asText()).isNotBlank();
    }

    @Test
    void oReenvioDevolveOMesmoResultadoENaoCriaOutro() throws Exception {
        // O entregador que aperta "sincronizar" duas vezes num sinal ruim não pode registrar duas
        // entregas para o mesmo cliente.
        var session = login();
        var cena = cenaNaRua(session);
        var aparelho = UUID.randomUUID();
        var operacaoId = UUID.randomUUID();

        var primeira = sincroniza(session, aparelho,
                operacao(operacaoId, 1, cena, "DELIVERED", cena.keg()));
        var provaId = JSON.readTree(primeira).get(0).get("resultId").asText();

        var reenvio = sincroniza(session, aparelho,
                operacao(operacaoId, 1, cena, "DELIVERED", cena.keg()));

        assertThat(JSON.readTree(reenvio).get(0).get("status").asText()).isEqualTo("DUPLICATE");
        assertThat(JSON.readTree(reenvio).get(0).get("resultId").asText()).isEqualTo(provaId);

        // E a parada continua com UMA prova: o reenvio não escreveu nada.
        mockMvc.perform(get("/api/v1/distribution/stops/" + cena.stopId() + "/proof")
                        .session(session))
                .andExpect(jsonPath("$.length()", is(1)));
    }

    @Test
    void oMesmoIdDeOutroAparelhoEOutraOperacao() throws Exception {
        // A idempotência é por (aparelho, operação): dois celulares podem sortear o mesmo UUID sem que
        // isso signifique nada.
        var session = login();
        var primeiraCena = cenaNaRua(session);
        var segundaCena = cenaNaRua(session);
        var operacaoId = UUID.randomUUID();

        sincroniza(session, UUID.randomUUID(),
                operacao(operacaoId, 1, primeiraCena, "DELIVERED", primeiraCena.keg()));
        var outro = sincroniza(session, UUID.randomUUID(),
                operacao(operacaoId, 1, segundaCena, "DELIVERED", segundaCena.keg()));

        assertThat(JSON.readTree(outro).get(0).get("status").asText()).isEqualTo("APPLIED");
    }

    @Test
    void oConflitoNaoSeResolveSozinhoEEsperaGente() throws Exception {
        // Alguém registrou a parada enquanto o aparelho estava sem sinal. Sobrescrever descartaria em
        // silêncio o registro de quem estava lá — ou o do escritório.
        var session = login();
        var cena = cenaNaRua(session);

        // O escritório registra primeiro, pela tela.
        mockMvc.perform(post(BASE + "/" + cena.loadId() + "/stops/" + cena.stopId() + "/proof")
                        .session(session).with(csrf()).contentType("application/json")
                        .content("""
                                {"outcome":"DELIVERED","delivered":["%s"],"collected":[]}
                                """.formatted(cena.keg())))
                .andExpect(status().isCreated());

        var corpo = sincroniza(session, UUID.randomUUID(),
                operacao(UUID.randomUUID(), 1, cena, "REFUSED", null));

        assertThat(JSON.readTree(corpo).get(0).get("status").asText()).isEqualTo("CONFLICTED");
        assertThat(JSON.readTree(corpo).get(0).get("reason").asText()).contains("já");

        // E ele aparece na fila de quem decide.
        mockMvc.perform(get(SYNC + "/conflicts").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.stopId == '" + cena.stopId() + "')]",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.empty())));
    }

    /**
     * A fila de uma carga só: é por ela que se investiga o dia de um entregador.
     *
     * <p>{@code /sync/conflicts} traz a fila da casa inteira e serve a quem decide; esta traz o que
     * aquele caminhão mandou, e serve a quem pergunta "o que aconteceu na rota de ontem". O endpoint não
     * tinha teste — e a diferença entre as duas leituras é o que faz a segunda existir.
     */
    @Test
    void aFilaDeUmaCargaTrazOQueAquelaRotaMandou() throws Exception {
        var session = login();
        var cena = cenaNaRua(session);
        var outra = cenaNaRua(session);

        sincroniza(session, UUID.randomUUID(),
                operacao(UUID.randomUUID(), 1, cena, "DELIVERED", cena.keg()));
        sincroniza(session, UUID.randomUUID(),
                operacao(UUID.randomUUID(), 1, outra, "DELIVERED", outra.keg()));

        // A da carga pedida vem...
        mockMvc.perform(get(SYNC + "/loads/" + cena.loadId()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.stopId == '" + cena.stopId() + "')]",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.empty())));

        // ...e a da outra NÃO. Sem este contraponto, um endpoint que devolvesse a fila inteira da casa
        // passaria no teste — e quem investiga uma rota leria as operações de outra.
        mockMvc.perform(get(SYNC + "/loads/" + cena.loadId()).session(session))
                .andExpect(jsonPath("$[?(@.stopId == '" + outra.stopId() + "')]",
                        org.hamcrest.Matchers.empty()));
    }

    @Test
    void umItemRecusadoNaoDerrubaOsOutros() throws Exception {
        // O entregador ficaria com o dia inteiro por sincronizar por causa de uma parada que o
        // escritório tocou.
        var session = login();
        var boa = cenaNaRua(session);
        var ruim = cenaNaRua(session);

        // A segunda carga é encerrada, então a operação dela não entra.
        mockMvc.perform(post(BASE + "/" + ruim.loadId() + "/close").session(session).with(csrf()))
                .andExpect(status().isNoContent());

        var corpo = sincroniza(session, UUID.randomUUID(),
                operacao(UUID.randomUUID(), 1, ruim, "DELIVERED", ruim.keg()),
                operacao(UUID.randomUUID(), 2, boa, "DELIVERED", boa.keg()));

        var resultado = JSON.readTree(corpo);
        assertThat(resultado.get(0).get("status").asText()).isEqualTo("REJECTED");
        assertThat(resultado.get(0).get("reason").asText()).isNotBlank();
        assertThat(resultado.get(1).get("status").asText()).isEqualTo("APPLIED");
    }

    @Test
    void aOrdemEadoAparelhoENaoADoPacote() throws Exception {
        // Aplicar fora dela entregaria antes de despachar. O lote chega embaralhado e é reordenado.
        var session = login();
        var primeira = cenaNaRua(session);
        var segunda = cenaNaRua(session);

        var corpo = sincroniza(session, UUID.randomUUID(),
                operacao(UUID.randomUUID(), 2, segunda, "DELIVERED", segunda.keg()),
                operacao(UUID.randomUUID(), 1, primeira, "DELIVERED", primeira.keg()));

        var resultado = JSON.readTree(corpo);
        assertThat(resultado.get(0).get("sequence").asInt()).isEqualTo(1);
        assertThat(resultado.get(1).get("sequence").asInt()).isEqualTo(2);
    }

    @Test
    void aHoraDoFatoEDoAparelho() throws Exception {
        // Usar a do servidor colocaria toda entrega offline no momento em que o caminhão voltou ao
        // depósito — e ninguém entregou nada no pátio às seis da tarde.
        var session = login();
        var cena = cenaNaRua(session);
        var noBar = Instant.now().minus(Duration.ofHours(6));

        var corpo = mockMvc.perform(post(SYNC).session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"deviceId":"%s","operations":[
                                  {"clientOperationId":"%s","sequence":1,"loadId":"%s","stopId":"%s",
                                   "outcome":"DELIVERED","occurredAt":"%s","delivered":["%s"],
                                   "collected":[]}]}
                                """.formatted(UUID.randomUUID(), UUID.randomUUID(), cena.loadId(),
                                cena.stopId(), noBar, cena.keg())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        var item = JSON.readTree(corpo).get(0);
        assertThat(Instant.parse(item.get("occurredAt").asText()))
                .isCloseTo(noBar, within(1, java.time.temporal.ChronoUnit.SECONDS));
        assertThat(Instant.parse(item.get("receivedAt").asText())).isAfter(noBar);
        assertThat(item.get("clockAhead").asBoolean()).isFalse();

        // E a prova gravada guarda a hora do BAR, e não a da sincronização.
        mockMvc.perform(get("/api/v1/distribution/stops/" + cena.stopId() + "/proof")
                        .session(session))
                .andExpect(jsonPath("$[0].occurredAt").exists());
    }

    @Test
    void oRelogioAdiantadoEMarcadoENaoRecusado() throws Exception {
        // O celular do entregador não se ajusta sozinho no subsolo do bar.
        var session = login();
        var cena = cenaNaRua(session);
        var futuro = Instant.now().plus(Duration.ofHours(3));

        var corpo = mockMvc.perform(post(SYNC).session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"deviceId":"%s","operations":[
                                  {"clientOperationId":"%s","sequence":1,"loadId":"%s","stopId":"%s",
                                   "outcome":"DELIVERED","occurredAt":"%s","delivered":["%s"],
                                   "collected":[]}]}
                                """.formatted(UUID.randomUUID(), UUID.randomUUID(), cena.loadId(),
                                cena.stopId(), futuro, cena.keg())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        var item = JSON.readTree(corpo).get(0);
        assertThat(item.get("status").asText()).isEqualTo("APPLIED");
        assertThat(item.get("clockAhead").asBoolean()).isTrue();
    }

    @Test
    void sincronizarTemAlcadaPropria() throws Exception {
        var session = login();
        var cena = cenaNaRua(session);

        mockMvc.perform(post(SYNC)
                        .with(authentication(principal(breweryOf(cena.loadId()),
                                Set.of("distribution.load.read"))))
                        .with(csrf()).contentType("application/json")
                        .content("{\"deviceId\":\"%s\",\"operations\":[%s]}"
                                .formatted(UUID.randomUUID(),
                                        operacao(UUID.randomUUID(), 1, cena, "DELIVERED", cena.keg()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void outraCervejariaNaoVeOsConflitosAlheios() throws Exception {
        var session = login();
        var cena = cenaNaRua(session);
        mockMvc.perform(post(BASE + "/" + cena.loadId() + "/stops/" + cena.stopId() + "/proof")
                        .session(session).with(csrf()).contentType("application/json")
                        .content("""
                                {"outcome":"DELIVERED","delivered":["%s"],"collected":[]}
                                """.formatted(cena.keg())))
                .andExpect(status().isCreated());
        sincroniza(session, UUID.randomUUID(), operacao(UUID.randomUUID(), 1, cena, "REFUSED", null));

        mockMvc.perform(get(SYNC + "/conflicts")
                        .with(authentication(principal(UUID.randomUUID(),
                                Set.of("distribution.load.read")))))
                .andExpect(jsonPath("$.length()", is(0)));
    }

    // --- ações ---

    private String sincroniza(MockHttpSession session, UUID aparelho, String... operacoes)
            throws Exception {
        return mockMvc.perform(post(SYNC).session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"deviceId\":\"%s\",\"operations\":[%s]}"
                                .formatted(aparelho, String.join(",", operacoes))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private static String operacao(UUID id, int sequencia, Cena cena, String desfecho, String keg) {
        var entregues = keg == null ? "" : "\"" + keg + "\"";
        var nota = keg == null ? ",\"note\":\"cliente recusou\"" : "";
        return """
                {"clientOperationId":"%s","sequence":%d,"loadId":"%s","stopId":"%s","outcome":"%s",
                 "occurredAt":"%s","delivered":[%s],"collected":[]%s}
                """.formatted(id, sequencia, cena.loadId(), cena.stopId(), desfecho, Instant.now(),
                entregues, nota);
    }

    private record Cena(String loadId, String stopId, String keg) {}

    /** Uma carga montada, conferida e já na rua. */
    private Cena cenaNaRua(MockHttpSession session) throws Exception {
        var carga = planejaCom(session, "1000");
        var parada = adicionaParada(session, carga, cliente(session), 1);
        var keg = vasilhames.pronto("KEG-" + UUID.randomUUID(), new java.math.BigDecimal("50"));
        poeNaCarga(session, carga, parada, keg);
        atribui(session, carga);
        mockMvc.perform(post(BASE + "/" + carga + "/release")
                        .with(authentication(conferente(breweryOf(carga)))).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(post(BASE + "/" + carga + "/depart").session(session).with(csrf()))
                .andExpect(status().isNoContent());
        return new Cena(carga, parada, keg.toString());
    }

    private void atribui(MockHttpSession session, String carga) throws Exception {
        mockMvc.perform(post(BASE + "/" + carga + "/driver").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"driverId\":\"%s\",\"vehicle\":\"ABC-1234\"}"
                                .formatted(motorista())))
                .andExpect(status().isNoContent());
    }

    private void poeNaCarga(MockHttpSession session, String carga, String parada, UUID keg)
            throws Exception {
        mockMvc.perform(post(BASE + "/" + carga + "/stops/" + parada + "/containers")
                        .session(session).with(csrf()).contentType("application/json")
                        .content("{\"containerId\":\"%s\"}".formatted(keg)))
                .andExpect(status().isNoContent());
    }

    private String adicionaParada(MockHttpSession session, String carga, String cliente, int seq)
            throws Exception {
        var body = mockMvc.perform(post(BASE + "/" + carga + "/stops").session(session).with(csrf())
                        .contentType("application/json").content(corpoParada(cliente, seq)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("id").asText();
    }

    private static String corpoParada(String cliente, int seq) {
        return """
                {"customerId":"%s","customerName":"Bar do Bruno","sequence":%d}
                """.formatted(cliente, seq);
    }

    private String planeja(MockHttpSession session) throws Exception {
        return planejaCom(session, "1000");
    }

    private String planejaCom(MockHttpSession session, String capacidade) throws Exception {
        var body = mockMvc.perform(post(BASE).session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"code":"CG-%s","scheduledFor":"%s","capacityLiters":%s}
                                """.formatted(UUID.randomUUID().toString().substring(0, 8),
                                LocalDate.now(), capacidade)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("id").asText();
    }

    private String cliente(MockHttpSession session) throws Exception {
        var body = mockMvc.perform(post("/api/v1/crm/customers").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"legalName\":\"Bar do Bruno %s\"}"
                                .formatted(UUID.randomUUID().toString().substring(0, 6))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("id").asText();
    }

    /** Um motorista de verdade: a carga aponta para {@code security_user}. */
    private UUID motorista() {
        return jdbc.sql("SELECT id FROM security_user WHERE normalized_email = 'admin@brassia.local'")
                .query(UUID.class).single();
    }

    private UUID breweryOf(String loadId) {
        return jdbc.sql("SELECT brewery_id FROM distribution_load WHERE id = :i")
                .param("i", UUID.fromString(loadId)).query(UUID.class).single();
    }

    private MockHttpSession login() throws Exception {
        var result = mockMvc.perform(post("/api/v1/security/login").with(csrf())
                        .contentType("application/json")
                        .content("{\"email\":\"admin@brassia.local\",\"password\":\"admin-local-123\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    /** Um conferente de verdade, com a alçada de liberar e sem a de montar. */
    private Authentication conferente(UUID breweryId) {
        var id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO security_user (id, email, normalized_email, display_name, status)
                VALUES (:id, :email, :email, 'Conferente', 'ACTIVE')
                """)
                .param("id", id).param("email", "conf-" + id + "@brassia.local").update();
        var p = new SecurityPrincipal(id, breweryId, "Conferente",
                Set.of("distribution.load.release", "distribution.load.read"));
        return new UsernamePasswordAuthenticationToken(p, "n/a", Set.of());
    }

    private Authentication principal(UUID breweryId, Set<String> permissions) {
        var p = new SecurityPrincipal(UUID.randomUUID(), breweryId, "Tester", permissions);
        return new UsernamePasswordAuthenticationToken(p, "n/a", Set.of());
    }

}
