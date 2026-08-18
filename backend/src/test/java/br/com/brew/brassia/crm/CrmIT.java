package br.com.brew.brassia.crm;

import org.springframework.jdbc.core.simple.JdbcClient;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
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
import java.time.Duration;
import java.time.Instant;
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
 * Clientes, contatos e consentimentos de ponta a ponta (CRM-001).
 *
 * <p>O que estes testes fixam é o que separa este módulo de um cadastro comum: consentimento por
 * finalidade com base legal, um histórico que responde pelo passado, e um apagamento que apaga a pessoa
 * sem destruir o histórico comercial.
 */
@SpringBootTest
@Testcontainers
class CrmIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String CUSTOMERS = "/api/v1/crm/customers";

    @Autowired
    WebApplicationContext context;

    @Autowired
    JdbcClient jdbc;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void cadastraClienteEContatoEConsultaDeVolta() throws Exception {
        var session = login();
        var customerId = criaCliente(session, "Central Bebidas Ltda", "Bar Central", cnpj());

        mockMvc.perform(get(CUSTOMERS + "/" + customerId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName", is("Bar Central")))
                .andExpect(jsonPath("$.active", is(true)));

        criaContato(session, customerId, "Ana Ribeiro", "ana@barcentral.com.br");

        mockMvc.perform(get(CUSTOMERS + "/" + customerId + "/contacts").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].name", is("Ana Ribeiro")))
                .andExpect(jsonPath("$[0].anonymized", is(false)));
    }

    @Test
    void oNomeDeTelaCaiParaARazaoSocialQuandoNaoHaFantasia() throws Exception {
        var session = login();
        var id = criaCliente(session, "Bar do Zé ME", null, cnpj());

        mockMvc.perform(get(CUSTOMERS + "/" + id).session(session))
                .andExpect(jsonPath("$.displayName", is("Bar do Zé ME")));
    }

    @Test
    void oDocumentoRepetidoNaMesmaCervejariaERecusadoCom409() throws Exception {
        var session = login();
        var doc = cnpj();
        criaCliente(session, "Primeiro Ltda", null, doc);

        mockMvc.perform(post(CUSTOMERS).session(session).with(csrf())
                        .contentType("application/json")
                        .content(JSON.writeValueAsString(new java.util.LinkedHashMap<>(java.util.Map.of(
                                "legalName", "Segundo Ltda", "taxId", doc)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("crm_duplicate_tax_id")))
                .andExpect(jsonPath("$.taxId", is(doc)));
    }

    @Test
    void clienteSemDocumentoNaoColideComOutroSemDocumento() throws Exception {
        // Índice parcial: nulo é o estado de quem ainda não mandou o documento, e é o caso mais comum
        // no começo. Um UNIQUE comum trataria dois cadastros vazios como duplicata.
        var session = login();
        criaCliente(session, "Sem Documento Um", null, null);
        criaCliente(session, "Sem Documento Dois", null, null);
    }

    @Test
    void oConsentimentoEPorFinalidadeEAContratualNaoDepende() throws Exception {
        var session = login();
        var customerId = criaCliente(session, "Central Bebidas Ltda", "Bar Central", cnpj());
        var contactId = criaContato(session, customerId, "Ana", "ana@bar.com.br");

        registraConsentimento(session, contactId, "MARKETING", true, Instant.now(), "formulário do site");

        // MARKETING liberado, SURVEY não — aceitar oferta não é aceitar pesquisa. E TRANSACTIONAL
        // continua liberado sem ninguém ter consentido: ele se apoia em contrato.
        mockMvc.perform(get(CUSTOMERS + "/" + customerId + "/contacts").session(session))
                .andExpect(jsonPath("$[0].purposes[?(@.purpose=='MARKETING')].allowedNow", is(java.util.List.of(true))))
                .andExpect(jsonPath("$[0].purposes[?(@.purpose=='SURVEY')].allowedNow", is(java.util.List.of(false))))
                .andExpect(jsonPath("$[0].purposes[?(@.purpose=='TRANSACTIONAL')].allowedNow", is(java.util.List.of(true))))
                .andExpect(jsonPath("$[0].purposes[?(@.purpose=='TRANSACTIONAL')].basis", is(java.util.List.of("CONTRACT"))));
    }

    @Test
    void naoSeRegistraConsentimentoParaFinalidadeContratual() throws Exception {
        // Registrar daria a entender que o aviso de entrega depende de consentimento, e abriria a porta
        // para alguém revogá-lo — e a cervejaria ficaria proibida de cumprir o que vendeu.
        var session = login();
        var customerId = criaCliente(session, "Central Bebidas Ltda", null, cnpj());
        var contactId = criaContato(session, customerId, "Ana", "ana@bar.com.br");

        mockMvc.perform(post(CUSTOMERS + "/contacts/" + contactId + "/consents").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"purpose":"TRANSACTIONAL","granted":true,"decidedAt":"%s","source":"contrato"}
                                """.formatted(Instant.now())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void revogarNaoApagaOHistoricoEOLivroSoCresce() throws Exception {
        var session = login();
        var customerId = criaCliente(session, "Central Bebidas Ltda", null, cnpj());
        var contactId = criaContato(session, customerId, "Ana", "ana@bar.com.br");

        var marco = Instant.now().minus(Duration.ofDays(60));
        var hoje = Instant.now();
        registraConsentimento(session, contactId, "MARKETING", true, marco, "site");
        registraConsentimento(session, contactId, "MARKETING", false, hoje, "e-mail de descadastro");

        mockMvc.perform(get(CUSTOMERS + "/" + customerId + "/contacts").session(session))
                .andExpect(jsonPath("$[0].purposes[?(@.purpose=='MARKETING')].allowedNow", is(java.util.List.of(false))))
                // As duas decisões continuam lá: é o histórico que responde "ela aceitava em março?".
                .andExpect(jsonPath("$[0].consentHistory.length()", is(2)))
                .andExpect(jsonPath("$[0].consentHistory[0].decision", is("GRANTED")))
                .andExpect(jsonPath("$[0].consentHistory[1].decision", is("REVOKED")));
    }

    @Test
    void anonimizarApagaAPessoaEMantemALinhaEOHistorico() throws Exception {
        var session = login();
        var customerId = criaCliente(session, "Central Bebidas Ltda", null, cnpj());
        var contactId = criaContato(session, customerId, "Ana Ribeiro", "ana@bar.com.br");
        registraConsentimento(session, contactId, "MARKETING", true, Instant.now(), "site");

        mockMvc.perform(post(CUSTOMERS + "/contacts/" + contactId + "/anonymize").session(session).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(CUSTOMERS + "/" + customerId + "/contacts").session(session))
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].id", is(contactId.toString())))
                .andExpect(jsonPath("$[0].name").doesNotExist())
                .andExpect(jsonPath("$[0].email").doesNotExist())
                .andExpect(jsonPath("$[0].anonymized", is(true)))
                // O registro de que ela pediu para sair fica: é como a cervejaria demonstra que atendeu.
                .andExpect(jsonPath("$[0].consentHistory.length()", is(1)))
                // E nada mais é permitido, nem transacional: a base contratual autoriza mandar o aviso,
                // ela não cria um endereço para onde mandar.
                .andExpect(jsonPath("$[0].purposes[?(@.purpose=='TRANSACTIONAL')].allowedNow", is(java.util.List.of(false))));
    }

    @Test
    void contatoAnonimizadoRecusaNovoConsentimentoCom409() throws Exception {
        var session = login();
        var customerId = criaCliente(session, "Central Bebidas Ltda", null, cnpj());
        var contactId = criaContato(session, customerId, "Ana", "ana@bar.com.br");
        mockMvc.perform(post(CUSTOMERS + "/contacts/" + contactId + "/anonymize").session(session).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(post(CUSTOMERS + "/contacts/" + contactId + "/consents").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"purpose":"MARKETING","granted":true,"decidedAt":"%s","source":"site"}
                                """.formatted(Instant.now())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("crm_contact_anonymized")));
    }

    @Test
    void clienteDesativadoSomeDaListaPadraoMasContinuaExistindo() throws Exception {
        // Não se apaga: é o histórico de expedição que um recall percorre para saber a quem avisar.
        var session = login();
        var id = criaCliente(session, "Fechou as Portas Ltda", null, cnpj());

        mockMvc.perform(put(CUSTOMERS + "/" + id + "/active").session(session).with(csrf())
                        .contentType("application/json").content("{\"active\":false}"))
                .andExpect(status().isOk());

        mockMvc.perform(get(CUSTOMERS + "/" + id).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(false)))
                .andExpect(jsonPath("$.legalName", is("Fechou as Portas Ltda")));
    }

    @Test
    void semPoliticaDeRetencaoNadaExpira() throws Exception {
        var session = login();

        mockMvc.perform(get("/api/v1/crm/retention-policy").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.daysAfterLastInteraction").doesNotExist());

        mockMvc.perform(put("/api/v1/crm/retention-policy").session(session).with(csrf())
                        .contentType("application/json").content("{\"daysAfterLastInteraction\":365}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.daysAfterLastInteraction", is(365)));
    }

    @Test
    void aFilaDeRetencaoListaQuemVenceuEDizDeOndeVeioADataConta() throws Exception {
        // DUV-CRM-001. A anonimização continua ato humano; o que faltava era dizer QUEM venceu — sem
        // isso, exigir revisão manual faz a fila crescer até ninguém olhar.
        var session = login();
        var customerId = criaCliente(session, "Bar Antigo Ltda", null, cnpj());
        var contactId = criaContato(session, customerId, "Bruno", "bruno@bar.com.br");

        // Sem política, nada vence: mostrar fila baseada num prazo que ninguém escolheu convidaria a
        // anonimizar por engano.
        mockMvc.perform(get("/api/v1/crm/customers/contacts/retention-queue").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(0)));

        // A política é da cervejaria inteira, e o teste a devolve como encontrou no fim: mexer nela pelo
        // endpoint deixaria o estado sujo para quem rodar depois.
        var brewery = breweryOfContact(contactId);
        jdbc.sql("""
                INSERT INTO crm_retention_policy (brewery_id, days_after_last_interaction, updated_by,
                                                  updated_at)
                VALUES (:brewery, 30, :by, now())
                ON CONFLICT (brewery_id) DO UPDATE SET days_after_last_interaction = 30
                """)
                .param("brewery", brewery).param("by", usuarioAdmin()).update();

        // Um contato recém-cadastrado, sem pedido, entrega ou consentimento, NÃO vence: cadastro que
        // nunca foi usado não é cliente vencido.
        mockMvc.perform(get("/api/v1/crm/customers/contacts/retention-queue").session(session))
                .andExpect(jsonPath("$[?(@.contactId == '" + contactId + "')]", empty()));

        // Com um consentimento antigo, o relógio anda — e a fila diz de onde veio a conta.
        jdbc.sql("""
                INSERT INTO crm_consent_entry (id, brewery_id, contact_id, purpose, decision,
                                               decided_at, source, recorded_by, recorded_at)
                VALUES (:id, :brewery, :contact, 'MARKETING', 'GRANTED',
                        now() - interval '400 days', 'formulário do site', :by, now())
                """)
                .param("id", UUID.randomUUID()).param("brewery", brewery)
                .param("contact", contactId)
                .param("by", usuarioAdmin())
                .update();

        mockMvc.perform(get("/api/v1/crm/customers/contacts/retention-queue").session(session))
                .andExpect(jsonPath("$[?(@.contactId == '" + contactId + "')].source",
                        contains("último consentimento")))
                .andExpect(jsonPath("$[?(@.contactId == '" + contactId + "')].dueSince",
                        org.hamcrest.Matchers.notNullValue()));

        jdbc.sql("DELETE FROM crm_retention_policy WHERE brewery_id = :b").param("b", brewery).update();
    }

    @Test
    void aFilaDeRetencaoTemAlcadaDeAnonimizar() throws Exception {
        // Ver quem está prestes a ser anonimizado é ver dado pessoal com o relógio correndo: quem não
        // pode anonimizar não precisa da lista.
        var session = login();

        mockMvc.perform(get("/api/v1/crm/customers/contacts/retention-queue")
                        .with(authentication(principal(UUID.randomUUID(), Set.of("crm.customer.read")))))
                .andExpect(status().isForbidden());
    }

    @Test
    void prazoDeRetencaoNaoPositivoERecusado() throws Exception {
        var session = login();

        mockMvc.perform(put("/api/v1/crm/retention-policy").session(session).with(csrf())
                        .contentType("application/json").content("{\"daysAfterLastInteraction\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void negaSemPermissaoEIsolaPorCervejaria() throws Exception {
        var session = login();
        var customerId = criaCliente(session, "Central Bebidas Ltda", null, cnpj());
        var contactId = criaContato(session, customerId, "Ana", "ana@bar.com.br");

        // Quem só lê não cadastra.
        mockMvc.perform(post(CUSTOMERS).with(authentication(principal(UUID.randomUUID(),
                        Set.of("crm.customer.read")))).with(csrf())
                        .contentType("application/json").content("{\"legalName\":\"Tentativa\"}"))
                .andExpect(status().isForbidden());

        // Cadastrar contato não dá o direito de APAGAR uma pessoa: a permissão é própria e crítica.
        mockMvc.perform(post(CUSTOMERS + "/contacts/" + contactId + "/anonymize")
                        .with(authentication(principal(UUID.randomUUID(), Set.of("crm.customer.manage"))))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        // E definir o prazo de retenção da casa também não vem junto com o cadastro.
        mockMvc.perform(put("/api/v1/crm/retention-policy")
                        .with(authentication(principal(UUID.randomUUID(), Set.of("crm.customer.manage"))))
                        .with(csrf())
                        .contentType("application/json").content("{\"daysAfterLastInteraction\":30}"))
                .andExpect(status().isForbidden());

        // Outra cervejaria não enxerga o cliente — e recebe 404, não 403: distinguir contaria que o
        // identificador existe em algum lugar.
        mockMvc.perform(get(CUSTOMERS + "/" + customerId)
                        .with(authentication(principal(UUID.randomUUID(), Set.of("crm.customer.read")))))
                .andExpect(status().isNotFound());
    }

    private UUID criaCliente(MockHttpSession session, String legalName, String tradeName, String taxId)
            throws Exception {
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("legalName", legalName);
        body.put("tradeName", tradeName);
        body.put("taxId", taxId);
        var result = mockMvc.perform(post(CUSTOMERS).session(session).with(csrf())
                        .contentType("application/json").content(JSON.writeValueAsString(body)))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(JSON.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    private UUID criaContato(MockHttpSession session, UUID customerId, String name, String email)
            throws Exception {
        var result = mockMvc.perform(post(CUSTOMERS + "/" + customerId + "/contacts").session(session)
                        .with(csrf()).contentType("application/json")
                        .content("{\"name\":\"%s\",\"email\":\"%s\"}".formatted(name, email)))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(JSON.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    private void registraConsentimento(MockHttpSession session, UUID contactId, String purpose,
            boolean granted, Instant decidedAt, String source) throws Exception {
        mockMvc.perform(post(CUSTOMERS + "/contacts/" + contactId + "/consents").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"purpose":"%s","granted":%s,"decidedAt":"%s","source":"%s"}
                                """.formatted(purpose, granted, decidedAt, source)))
                .andExpect(status().isNoContent());
    }

    /** Documento único por teste: os testes compartilham o banco, e o índice é por cervejaria. */
    private static String cnpj() {
        return UUID.randomUUID().toString().substring(0, 18);
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

    private UUID breweryOfContact(UUID contactId) {
        return jdbc.sql("SELECT brewery_id FROM crm_contact WHERE id = :i")
                .param("i", contactId).query(UUID.class).single();
    }

    private UUID usuarioAdmin() {
        return jdbc.sql("SELECT id FROM security_user WHERE normalized_email = 'admin@brassia.local'")
                .query(UUID.class).single();
    }
}
