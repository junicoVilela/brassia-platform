package br.com.brew.brassia.fieldfeedback;

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
import java.util.Set;
import java.util.UUID;
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
 * Feedback de campo de ponta a ponta (FLD-001).
 *
 * <p>O que só aparece aqui: que a resposta da reclamação <strong>não carrega dado pessoal</strong> nem por
 * acidente, que a leitura do contato é auditada de verdade, e que o apagamento esvazia as colunas no
 * PostgreSQL preservando a reclamação. Nenhuma das três se prova com dublê — a primeira é sobre o JSON que
 * sai, e as outras duas sobre o que sobra no banco.
 */
@SpringBootTest
@Testcontainers
class FieldFeedbackIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String COMPLAINTS = "/api/v1/field-feedback/complaints";

    @Autowired WebApplicationContext context;
    @Autowired JdbcClient jdbc;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("reclamação de preferência não exige nada e encerra direto")
    void preferenciaEncerraDireto() throws Exception {
        var session = login();
        var id = idOf(register(session, "OFF_FLAVOR", "PREFERENCE", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requiredActions.length()").value(0)));

        mockMvc.perform(post(COMPLAINTS + "/" + id + "/closure").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"note\":\"Cliente orientado sobre o estilo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    @DisplayName("CORPO ESTRANHO EXIGE QUARENTENA mesmo classificado como severidade baixa")
    void corpoEstranhoExige() throws Exception {
        // Quem registra pode classificar como QUALITY por não querer alarmar. A exigência não cai junto.
        var session = login();

        register(session, "FOREIGN_BODY", "QUALITY", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requiredActions[?(@.code=='QUARANTINE')]").exists())
                .andExpect(jsonPath("$.requiredActions[?(@.code=='ROOT_CAUSE_ANALYSIS')]").exists())
                .andExpect(jsonPath("$.pendingActions.length()").value(2));
    }

    @Test
    @DisplayName("NÃO ENCERRA COM AÇÃO PENDENTE — 422 listando quais faltam")
    void naoEncerraComPendencia() throws Exception {
        // É o que impede uma reclamação de corpo estranho de virar "cliente contatado, caso resolvido".
        var session = login();
        var id = idOf(register(session, "FOREIGN_BODY", "SAFETY", null).andExpect(status().isCreated()));

        mockMvc.perform(post(COMPLAINTS + "/" + id + "/closure").session(session).with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("pending_required_actions"))
                .andExpect(jsonPath("$.pendingActions.length()").value(2));
    }

    @Test
    @DisplayName("atender e dispensar liberam o encerramento; a dispensa fica assinada")
    void atenderEDispensar() throws Exception {
        var session = login();
        var id = idOf(register(session, "FOREIGN_BODY", "SAFETY", null).andExpect(status().isCreated()));
        var quarentena = UUID.randomUUID();

        mockMvc.perform(post(COMPLAINTS + "/" + id + "/actions/QUARANTINE/fulfillment")
                        .session(session).with(csrf()).contentType("application/json")
                        .content("{\"referenceId\":\"" + quarentena + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingActions.length()").value(1));

        mockMvc.perform(post(COMPLAINTS + "/" + id + "/actions/ROOT_CAUSE_ANALYSIS/waiver")
                        .session(session).with(csrf()).contentType("application/json")
                        .content("{\"justification\":\"Fragmento identificado como externo, "
                                + "conforme laudo anexo ao protocolo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingActions.length()").value(0));

        mockMvc.perform(post(COMPLAINTS + "/" + id + "/closure").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.outcomes[?(@.action=='QUARANTINE')].referenceId")
                        .value(quarentena.toString()))
                .andExpect(jsonPath("$.outcomes[?(@.action=='ROOT_CAUSE_ANALYSIS')].decidedBy")
                        .exists());
    }

    @Test
    @DisplayName("dispensa sem justificativa de verdade é recusada")
    void dispensaFraca() throws Exception {
        var session = login();
        var id = idOf(register(session, "OFF_FLAVOR", "SYSTEMIC", null).andExpect(status().isCreated()));

        mockMvc.perform(post(COMPLAINTS + "/" + id + "/actions/ROOT_CAUSE_ANALYSIS/waiver")
                        .session(session).with(csrf()).contentType("application/json")
                        .content("{\"justification\":\"n/a\"}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("A RESPOSTA DA RECLAMAÇÃO NÃO CARREGA DADO PESSOAL")
    void respostaSemDadoPessoal() throws Exception {
        // O contato foi gravado; o JSON da reclamação não pode conter nada dele — nem na lista, nem no
        // detalhe. É a garantia estrutural da história, verificada sobre o JSON que realmente sai.
        var session = login();
        var contato = """
                {"name":"Fulana de Tal","email":"fulana@example.com","phone":"11999998888",
                 "address":"Rua das Flores, 100"}
                """;
        var id = idOf(register(session, "OFF_FLAVOR", "QUALITY", contato)
                .andExpect(status().isCreated()));

        for (var url : java.util.List.of(COMPLAINTS + "/" + id, COMPLAINTS)) {
            var body = mockMvc.perform(get(url).session(session))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            org.assertj.core.api.Assertions.assertThat(body)
                    .doesNotContain("Fulana").doesNotContain("fulana@example.com")
                    .doesNotContain("11999998888").doesNotContain("Rua das Flores");
        }
    }

    @Test
    @DisplayName("o contato só sai no endpoint próprio, e só com a permissão crítica")
    void contatoExigePermissaoPropria() throws Exception {
        var session = login();
        var contato = "{\"name\":\"Fulana de Tal\",\"phone\":\"11999998888\"}";
        var id = idOf(register(session, "OFF_FLAVOR", "QUALITY", contato)
                .andExpect(status().isCreated()));

        var brewery = breweryOf(session);
        var semPermissao = principal(brewery,
                Set.of("feedback.complaint.read", "feedback.complaint.write"));

        mockMvc.perform(get(COMPLAINTS + "/" + id + "/contact").with(authentication(semPermissao)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(COMPLAINTS + "/" + id + "/contact").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Fulana de Tal"))
                .andExpect(jsonPath("$.erased").value(false));
    }

    @Test
    @DisplayName("LER O CONTATO GERA AUDITORIA, inclusive quando não há contato")
    void leituraDeContatoAuditada() throws Exception {
        // Registrar só o acerto deixaria de fora quem varre reclamações procurando dados.
        var session = login();
        var id = idOf(register(session, "OFF_FLAVOR", "QUALITY", null).andExpect(status().isCreated()));

        var antes = auditCount("feedback.contact.read", id);
        mockMvc.perform(get(COMPLAINTS + "/" + id + "/contact").session(session))
                .andExpect(status().isNotFound());

        org.assertj.core.api.Assertions.assertThat(auditCount("feedback.contact.read", id))
                .isEqualTo(antes + 1);
    }

    @Test
    @DisplayName("APAGAR ESVAZIA AS COLUNAS E PRESERVA A RECLAMAÇÃO")
    void apagarPreservaReclamacao() throws Exception {
        // A investigação precisa durar anos; o telefone de quem ligou, não.
        var session = login();
        var contato = """
                {"name":"Fulana de Tal","email":"fulana@example.com","phone":"11999998888"}
                """;
        var id = idOf(register(session, "FOREIGN_BODY", "SAFETY", contato)
                .andExpect(status().isCreated()));

        mockMvc.perform(delete(COMPLAINTS + "/" + id + "/contact").session(session).with(csrf()))
                .andExpect(status().isNoContent());

        // O banco não guarda resto.
        var restos = jdbc.sql("""
                SELECT COUNT(*) FROM field_complaint_contact
                WHERE complaint_id = :id AND (name IS NOT NULL OR email IS NOT NULL
                      OR phone IS NOT NULL OR address IS NOT NULL)
                """).param("id", UUID.fromString(id)).query(Integer.class).single();
        org.assertj.core.api.Assertions.assertThat(restos).isZero();

        // O fato do apagamento fica: "anônima desde o início" e "apagada a pedido" continuam distintas.
        mockMvc.perform(get(COMPLAINTS + "/" + id + "/contact").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.erased").value(true))
                .andExpect(jsonPath("$.erasedAt").isNotEmpty())
                .andExpect(jsonPath("$.name").doesNotExist());

        // E a reclamação continua íntegra, com as exigências e o relato.
        mockMvc.perform(get(COMPLAINTS + "/" + id).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiredActions.length()").value(2))
                .andExpect(jsonPath("$.description").isNotEmpty());
    }

    @Test
    @DisplayName("apagar exige permissão própria")
    void apagarExigePermissao() throws Exception {
        var session = login();
        var id = idOf(register(session, "OFF_FLAVOR", "QUALITY", "{\"name\":\"Fulana\"}")
                .andExpect(status().isCreated()));
        var semPermissao = principal(breweryOf(session),
                Set.of("feedback.complaint.read", "feedback.contact.read"));

        mockMvc.perform(delete(COMPLAINTS + "/" + id + "/contact").with(csrf())
                        .with(authentication(semPermissao)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("armazenagem desconhecida é distinta de 'estava tudo bem'")
    void armazenagemDesconhecida() throws Exception {
        var session = login();

        register(session, "OFF_FLAVOR", "QUALITY", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.storage.conditionsKnown").value(false));

        var comStorage = """
                {"batchId":"%s","category":"OFF_FLAVOR","severity":"QUALITY",
                 "description":"Gosto de papelão","storage":{"temperatureCelsius":35,
                 "daysSincePurchase":14,"exposedToLight":true}}
                """.formatted(batch(session));
        mockMvc.perform(post(COMPLAINTS).session(session).with(csrf())
                        .contentType("application/json").content(comStorage))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.storage.conditionsKnown").value(true))
                .andExpect(jsonPath("$.storage.temperatureCelsius").value(35));
    }

    @Test
    @DisplayName("amostra retida sem local é recusada")
    void amostraSemLocal() throws Exception {
        var session = login();
        var body = """
                {"batchId":"%s","category":"OFF_FLAVOR","severity":"QUALITY","description":"x",
                 "sample":{"status":"RETAINED"}}
                """.formatted(batch(session));

        mockMvc.perform(post(COMPLAINTS).session(session).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("lote inexistente é recusado")
    void loteInexistente() throws Exception {
        var session = login();
        var body = """
                {"batchId":"%s","category":"OFF_FLAVOR","severity":"QUALITY","description":"x"}
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post(COMPLAINTS).session(session).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("unknown_complaint_batch"));
    }

    @Test
    @DisplayName("reclamação de outra cervejaria não é visível nem apagável")
    void isolaPorCervejaria() throws Exception {
        var session = login();
        var id = idOf(register(session, "OFF_FLAVOR", "QUALITY", "{\"name\":\"Fulana\"}")
                .andExpect(status().isCreated()));
        var outra = principal(UUID.randomUUID(), Set.of("feedback.complaint.read",
                "feedback.contact.read", "feedback.contact.erase"));

        mockMvc.perform(get(COMPLAINTS + "/" + id).with(authentication(outra)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(COMPLAINTS + "/" + id + "/contact").with(authentication(outra)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(COMPLAINTS + "/" + id + "/contact").with(csrf())
                        .with(authentication(outra)))
                .andExpect(status().isNotFound());

        // E o contato da cervejaria certa continua lá: o apagamento alheio não tocou nele.
        mockMvc.perform(get(COMPLAINTS + "/" + id + "/contact").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.erased").value(false));
    }

    @Test
    @DisplayName("sem permissão nenhuma, nada responde")
    void semPermissao() throws Exception {
        var nobody = principal(UUID.randomUUID(), Set.of());

        mockMvc.perform(get(COMPLAINTS).with(authentication(nobody)))
                .andExpect(status().isForbidden());
    }

    // --- infraestrutura ---

    private ResultActions register(MockHttpSession session, String categoria, String severidade,
            String contato) throws Exception {
        var body = """
                {"batchId":"%s","reference":"SAC-%s","category":"%s","severity":"%s",
                 "description":"Gosto de papelão na terceira lata"%s}
                """.formatted(batch(session), UUID.randomUUID().toString().substring(0, 6),
                categoria, severidade, contato == null ? "" : ",\"contact\":" + contato);
        return mockMvc.perform(post(COMPLAINTS).session(session).with(csrf())
                .contentType("application/json").content(body));
    }

    private int auditCount(String action, String resourceId) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM audit_event WHERE action = :action AND target_id = :target
                """)
                .param("action", action).param("target", resourceId)
                .query(Integer.class).single();
    }

    private UUID breweryOf(MockHttpSession session) throws Exception {
        var body = mockMvc.perform(get("/api/v1/security/session").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JSON.readTree(body).get("activeBrewery").get("id").asText());
    }

    private String batch(MockHttpSession session) throws Exception {
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var equipmentId = idOf(mockMvc.perform(post("/api/v1/equipment").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"fld-" + sfx + "\",\"name\":\"BH\",\"capacityLiters\":500,"
                                + "\"deadSpaceLiters\":20,\"mashEfficiencyPercent\":72,"
                                + "\"boilOffLitersPerHour\":8}"))
                .andExpect(status().isCreated()));
        var maltId = createIngredient(session, "MALT", "m-" + sfx, "KG",
                "{\"potentialSg\":\"1.037\",\"colorEbc\":\"4\"}");
        var hopId = createIngredient(session, "HOP", "h-" + sfx, "G", "{\"alphaAcid\":\"12\"}");
        var yeastId = createIngredient(session, "YEAST", "y-" + sfx, "UNIT", "{\"attenuation\":\"78\"}");
        var content = """
                {"name":"FLD %s","equipmentId":"%s","batchVolumeLiters":400,"boilTimeMinutes":60,
                 "targetIbu":30,
                 "items":[{"ingredientId":"%s","stage":"MASH","quantity":20,"unit":"KG"},
                          {"ingredientId":"%s","stage":"BOIL","quantity":60,"unit":"G","timingMinutes":60},
                          {"ingredientId":"%s","stage":"FERMENTATION","quantity":1,"unit":"UNIT"}]}
                """.formatted(sfx, equipmentId, maltId, hopId, yeastId);
        var recipeId = idOf(mockMvc.perform(post("/api/v1/recipes").session(session).with(csrf())
                        .contentType("application/json").content(content))
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

        var listBody = mockMvc.perform(get("/api/v1/production/batches").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        // A listagem passou a ser paginada (REL-002): o array vem em `content`.
        for (var node : JSON.readTree(listBody).get("content")) {
            if (node.get("orderId").asText().equals(orderId)) {
                return node.get("id").asText();
            }
        }
        throw new IllegalStateException("lote não encontrado para a ordem " + orderId);
    }

    private String createIngredient(MockHttpSession session, String type, String code, String unit,
            String attributes) throws Exception {
        return idOf(mockMvc.perform(post("/api/v1/catalog/ingredients").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"type\":\"" + type + "\",\"code\":\"" + code + "\",\"name\":\"" + code
                                + "\",\"useUnit\":\"" + unit + "\",\"purchaseUnit\":\"" + unit
                                + "\",\"attributes\":" + attributes + "}"))
                .andExpect(status().isCreated()));
    }

    private static String idOf(ResultActions actions) throws Exception {
        return JSON.readTree(actions.andReturn().getResponse().getContentAsString()).get("id").asText();
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
