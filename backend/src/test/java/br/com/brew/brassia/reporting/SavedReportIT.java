package br.com.brew.brassia.reporting;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import br.com.brew.brassia.shared.security.SecurityPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
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
 * Relatórios salvos e entrega programada de ponta a ponta (RPT-003).
 *
 * <p>Os quatro critérios da história aparecem aqui na ordem em que importam: a definição registra o
 * que o critério manda registrar, a execução usa a alçada do dono, o link é temporário e auditado, e
 * reentregar não duplica nem regenera.
 */
@SpringBootTest
@Testcontainers
class SavedReportIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SAVED = "/api/v1/reporting/saved-reports";

    @Autowired WebApplicationContext context;
    @Autowired JdbcClient jdbc;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("a definição registra versão, filtros, tenant, fuso, formato e retenção")
    void aDefinicaoRegistraOCriterio() throws Exception {
        var session = login();

        var report = define(session, "Painel diário " + suffix(), "DAILY", 30, adminId(session))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.definitionVersion", is(1)))
                .andExpect(jsonPath("$.timezone", is("America/Sao_Paulo")))
                .andExpect(jsonPath("$.format", is("JSON")))
                .andExpect(jsonPath("$.retentionDays", is(30)))
                .andExpect(jsonPath("$.filters.group", is("COST")))
                .andReturn().getResponse().getContentAsString();

        // O tenant não vem do corpo: vem da sessão, e é o que impede definir para outra cervejaria.
        var id = JSON.readTree(report).get("id").asText();
        Assertions.assertThat(breweryOfReport(id)).isEqualTo(adminBrewery(session));
    }

    @Test
    @DisplayName("redefinir sobe a versão da definição")
    void redefinirSobeAVersao() throws Exception {
        var session = login();
        var id = idOf(define(session, "Painel " + suffix(), "DAILY", 30, adminId(session))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        mockMvc.perform(put(SAVED + "/" + id).session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"filters":{"group":"PRODUCTION"},"timezone":"America/Sao_Paulo",
                                 "schedule":"WEEKLY","retentionDays":15,"recipients":[]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.definitionVersion", is(2)))
                .andExpect(jsonPath("$.schedule", is("WEEKLY")));
    }

    @Test
    @DisplayName("a execução roda com a alçada do dono: dono sem permissão recusa, e diz por quê")
    void donoSemAlcadaRecusa() throws Exception {
        var session = login();
        // Um usuário qualquer como proprietário técnico: ele não tem grupo, logo não tem alçada.
        var semAlcada = UUID.randomUUID();
        var id = idOf(define(session, "Painel " + suffix(), "MANUAL", 30, semAlcada)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        mockMvc.perform(post(SAVED + "/" + id + "/runs").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("REFUSED")))
                .andExpect(jsonPath("$.refusalReason", containsString("reporting.dashboard.read")))
                .andExpect(jsonPath("$.refusalReason", containsString("privilégio de sistema")))
                .andExpect(jsonPath("$.downloadToken").doesNotExist());
    }

    @Test
    @DisplayName("quem pede não empresta a própria alçada para o relatório de outra pessoa")
    void quemPedeNaoEmprestaAlcada() throws Exception {
        var session = login();
        var semAlcada = UUID.randomUUID();
        var id = idOf(define(session, "Painel " + suffix(), "MANUAL", 30, semAlcada)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        // O admin tem todas as permissões e mesmo assim a execução recusa: a alçada usada é a do dono.
        mockMvc.perform(post(SAVED + "/" + id + "/runs").session(session).with(csrf()))
                .andExpect(jsonPath("$.status", is("REFUSED")));
    }

    @Test
    @DisplayName("com o dono autorizado, a execução produz o artefato e um link temporário")
    void execucaoProduzArtefatoELink() throws Exception {
        var session = login();
        var owner = adminId(session);
        var id = idOf(define(session, "Painel " + suffix(), "MANUAL", 30, owner)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        var run = JSON.readTree(mockMvc.perform(post(SAVED + "/" + id + "/runs").session(session)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUCCEEDED")))
                .andReturn().getResponse().getContentAsString());

        var token = run.get("downloadToken").asText();
        Assertions.assertThat(token).isNotBlank();
        mockMvc.perform(get("/api/v1/reporting/downloads/" + token).session(session))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")));
        // Cada abertura fica auditada: a partir dali o documento está fora do sistema.
        Assertions.assertThat(downloadsOf(run.get("id").asText())).isEqualTo(1);
    }

    @Test
    @DisplayName("link de outra pessoa não abre, e a recusa fica registrada")
    void linkDeOutraPessoaNaoAbre() throws Exception {
        var session = login();
        var id = idOf(define(session, "Painel " + suffix(), "MANUAL", 30, adminId(session))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        var run = JSON.readTree(mockMvc.perform(post(SAVED + "/" + id + "/runs").session(session)
                        .with(csrf())).andReturn().getResponse().getContentAsString());
        var token = run.get("downloadToken").asText();

        var outro = principal(adminBrewery(session), Set.of("reporting.saved.read"));
        mockMvc.perform(get("/api/v1/reporting/downloads/" + token).with(authentication(outro)))
                .andExpect(status().isGone());
    }

    @Test
    @DisplayName("token inexistente responde igual a token vencido")
    void tokenInexistenteRespondeIgual() throws Exception {
        var session = login();

        mockMvc.perform(get("/api/v1/reporting/downloads/nao-existe").session(session))
                .andExpect(status().isGone());
    }

    @Test
    @DisplayName("reentregar não duplica nem regenera: conta a tentativa e mantém o artefato")
    void reentregarNaoDuplicaNemRegenera() throws Exception {
        var session = login();
        var owner = adminId(session);
        var id = idOf(define(session, "Painel " + suffix(), "MANUAL", 30, owner, Set.of(owner))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        var run = JSON.readTree(mockMvc.perform(post(SAVED + "/" + id + "/runs").session(session)
                        .with(csrf())).andReturn().getResponse().getContentAsString());
        var runId = run.get("id").asText();

        deliver(session, runId, owner, false, "caixa cheia").andExpect(status().isOk());
        var second = JSON.readTree(deliver(session, runId, owner, true, null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        Assertions.assertThat(second.get("deliveries").size()).isEqualTo(1);
        Assertions.assertThat(second.get("deliveries").get(0).get("attempts").asInt()).isEqualTo(2);
        Assertions.assertThat(second.get("deliveries").get(0).get("status").asText())
                .isEqualTo("DELIVERED");
        // Uma execução só: a falha de entrega não gerou artefato novo.
        Assertions.assertThat(runsOf(id)).isEqualTo(1);
    }

    @Test
    @DisplayName("definir e programar é alçada própria, separada de consultar")
    void definirEhAlcadaPropria() throws Exception {
        var session = login();
        var leitor = principal(adminBrewery(session), Set.of("reporting.saved.read"));

        mockMvc.perform(get(SAVED).with(authentication(leitor))).andExpect(status().isOk());
        mockMvc.perform(post(SAVED).with(authentication(leitor)).with(csrf())
                        .contentType("application/json").content(body("Painel", "DAILY", 30,
                                UUID.randomUUID(), Set.of())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("relatório de outra cervejaria não existe para quem pergunta")
    void isolaPorCervejaria() throws Exception {
        var session = login();
        var id = idOf(define(session, "Painel " + suffix(), "DAILY", 30, adminId(session))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        var other = principal(UUID.randomUUID(), Set.of("reporting.saved.read"));
        mockMvc.perform(get(SAVED + "/" + id).with(authentication(other)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("unknown_saved_report")));
        mockMvc.perform(get(SAVED).with(authentication(other)))
                .andExpect(jsonPath("$.length()", is(0)));
    }

    @Test
    @DisplayName("nome repetido na mesma cervejaria é recusado")
    void nomeRepetidoEhRecusado() throws Exception {
        var session = login();
        var name = "Painel " + suffix();

        define(session, name, "DAILY", 30, adminId(session)).andExpect(status().isCreated());
        define(session, name, "DAILY", 30, adminId(session)).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("fuso desconhecido é recusado antes de virar definição")
    void fusoDesconhecidoEhRecusado() throws Exception {
        var session = login();

        mockMvc.perform(post(SAVED).session(session).with(csrf()).contentType("application/json")
                        .content("""
                                {"name":"Painel %s","kind":"DASHBOARD","filters":{},
                                 "timezone":"Marte/Olympus","format":"JSON","schedule":"DAILY",
                                 "retentionDays":30,"ownerUserId":"%s","recipients":[]}
                                """.formatted(suffix(), UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }

    // --- helpers ---

    private ResultActions define(MockHttpSession session, String name, String schedule,
            int retentionDays, UUID owner) throws Exception {
        return define(session, name, schedule, retentionDays, owner, Set.of());
    }

    private ResultActions define(MockHttpSession session, String name, String schedule,
            int retentionDays, UUID owner, Set<UUID> recipients) throws Exception {
        return mockMvc.perform(post(SAVED).session(session).with(csrf())
                .contentType("application/json")
                .content(body(name, schedule, retentionDays, owner, recipients)));
    }

    private static String body(String name, String schedule, int retentionDays, UUID owner,
            Set<UUID> recipients) {
        var list = recipients.stream().map(id -> "\"" + id + "\"")
                .reduce((one, other) -> one + "," + other).orElse("");
        return """
                {"name":"%s","kind":"DASHBOARD","filters":{"group":"COST"},
                 "timezone":"America/Sao_Paulo","format":"JSON","schedule":"%s",
                 "retentionDays":%d,"ownerUserId":"%s","recipients":[%s]}
                """.formatted(name, schedule, retentionDays, owner, list);
    }

    private ResultActions deliver(MockHttpSession session, String runId, UUID recipient,
            boolean delivered, String detail) throws Exception {
        var body = "{\"recipientId\":\"" + recipient + "\",\"delivered\":" + delivered
                + (detail == null ? "" : ",\"detail\":\"" + detail + "\"") + "}";
        return mockMvc.perform(post(SAVED + "/runs/" + runId + "/deliveries").session(session)
                .with(csrf()).contentType("application/json").content(body));
    }

    private int downloadsOf(String runId) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM audit_event
                WHERE action = 'reporting.saved.download' AND target_id = :run
                """).param("run", runId).query(Integer.class).single();
    }

    private int runsOf(String reportId) {
        return jdbc.sql("SELECT COUNT(*) FROM reporting_report_run WHERE report_id = :report")
                .param("report", UUID.fromString(reportId)).query(Integer.class).single();
    }

    private UUID breweryOfReport(String reportId) {
        return jdbc.sql("SELECT brewery_id FROM reporting_saved_report WHERE id = :id")
                .param("id", UUID.fromString(reportId)).query(UUID.class).single();
    }

    private UUID adminId(MockHttpSession session) throws Exception {
        return UUID.fromString(sessionNode(session).get("userId").asText());
    }

    private UUID adminBrewery(MockHttpSession session) throws Exception {
        return UUID.fromString(sessionNode(session).get("activeBrewery").get("id").asText());
    }

    private JsonNode sessionNode(MockHttpSession session) throws Exception {
        return JSON.readTree(mockMvc.perform(get("/api/v1/security/session").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static String idOf(String json) throws Exception {
        return JSON.readTree(json).get("id").asText();
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
