package br.com.brew.brassia.ai;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.UUID;
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
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * O gateway de IA de ponta a ponta, com o provedor desligado (AIA-001).
 *
 * <p><strong>Desligado é a configuração sob teste, e é o default do produto.</strong> Uma instalação sem
 * IA precisa continuar inteira: o status responde, a recusa é explícita e o teto de gasto é
 * administrável antes de existir qualquer provedor. Testar o caminho felizmente configurado exigiria
 * chave de terceiro num teste de CI — o que este teste garante é a promessa que vale para todo mundo.
 */
@SpringBootTest
@Testcontainers
class AiGatewayIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String GATEWAY = "/api/v1/ai/gateway";

    @Autowired WebApplicationContext context;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("com provedor desligado, o status responde: é estado normal, não erro")
    void statusRespondeComProvedorDesligado() throws Exception {
        var session = login();

        mockMvc.perform(get(GATEWAY).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled", is(false)))
                .andExpect(jsonPath("$.provider", is("anthropic")))
                .andExpect(jsonPath("$.models.length()", is(0)))
                .andExpect(jsonPath("$.timeoutSeconds", greaterThanOrEqualTo(1)))
                // Há sempre um teto: o desta cervejaria ou o padrão da instalação. Que ele exista é o
                // invariante; o valor depende de quem já mexeu nele (ver tetoEhAdministravel).
                .andExpect(jsonPath("$.budget.monthlyLimit").exists())
                .andExpect(jsonPath("$.budget.currency").exists())
                .andExpect(jsonPath("$.budget.remaining").exists());
    }

    @Test
    @DisplayName("a verificação recusa com Problem Details e não deixa o fluxo pendurado")
    void probeRecusaComProblemDetails() throws Exception {
        var session = login();

        mockMvc.perform(post(GATEWAY + "/probe").with(csrf()).session(session))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.code", is("ai_provider_disabled")))
                .andExpect(jsonPath("$.traceId").exists())
                .andExpect(jsonPath("$.type").exists());
    }

    @Test
    @DisplayName("a tentativa recusada aparece no ledger — quem opera vê que alguém tentou")
    void tentativaRecusadaEntraNoLedger() throws Exception {
        var session = login();
        mockMvc.perform(post(GATEWAY + "/probe").with(csrf()).session(session))
                .andExpect(status().isNotImplemented());

        mockMvc.perform(get(GATEWAY).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recent.length()", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.recent[0].status", is("PROVIDER_DISABLED")))
                .andExpect(jsonPath("$.recent[0].purpose", is("CONNECTIVITY_PROBE")))
                .andExpect(jsonPath("$.recent[0].cost", is(0.0)))
                // Sem gasto porque não houve chamada; com motivo porque houve tentativa.
                .andExpect(jsonPath("$.recent[0].failureReason").exists());
    }

    @Test
    @DisplayName("o teto é administrável antes de existir provedor, e a versão avança")
    void tetoEhAdministravel() throws Exception {
        var session = login();
        var version = currentBudgetVersion(session);

        mockMvc.perform(budgetRequest(session, "120.00", version))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyLimit", is(120.00)))
                .andExpect(jsonPath("$.version", is((int) version + 1)));

        mockMvc.perform(budgetRequest(session, "200.00", version + 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyLimit", is(200.00)))
                .andExpect(jsonPath("$.version", is((int) version + 2)));
    }

    @Test
    @DisplayName("versão velha não sobrescreve a decisão de outra pessoa")
    void versaoVelhaEhRecusada() throws Exception {
        var session = login();
        var version = currentBudgetVersion(session);

        // Duas pessoas leram a mesma versão. A primeira grava e a versão avança.
        mockMvc.perform(budgetRequest(session, "120.00", version)).andExpect(status().isOk());

        // A segunda chega com a versão que leu, que já não é a atual.
        mockMvc.perform(budgetRequest(session, "5.00", version))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("ai_budget_stale")));

        // O freio da primeira pessoa continua valendo: a segunda escrita não passou por cima.
        mockMvc.perform(get(GATEWAY).session(session))
                .andExpect(jsonPath("$.budget.monthlyLimit", is(120.00)));
    }

    @Test
    @DisplayName("teto negativo é recusado no contrato, antes de chegar ao domínio")
    void tetoNegativoEhRecusado() throws Exception {
        var session = login();

        mockMvc.perform(put(GATEWAY + "/budget").with(csrf()).session(session)
                        .contentType("application/json")
                        .content("{\"monthlyLimit\": -1.00, \"version\": 0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("sem permissão, nenhuma das três operações responde")
    void semPermissaoNadaResponde() throws Exception {
        var nobody = principal(UUID.randomUUID(), Set.of());

        mockMvc.perform(get(GATEWAY).with(authentication(nobody)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(GATEWAY + "/probe").with(csrf()).with(authentication(nobody)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(GATEWAY + "/budget").with(csrf()).with(authentication(nobody))
                        .contentType("application/json")
                        .content("{\"monthlyLimit\": 10.00, \"version\": 0}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ler o gateway não dá poder de mexer no freio")
    void leituraNaoDaPoderDeAlterarOTeto() throws Exception {
        // A separação de permissões é o ponto: quem só acompanha o custo não decide o teto, e quem só
        // consulta não dispara uma verificação que custa a cada clique.
        var reader = principal(UUID.randomUUID(), Set.of("ai.gateway.read"));

        mockMvc.perform(get(GATEWAY).with(authentication(reader)))
                .andExpect(status().isOk());
        mockMvc.perform(post(GATEWAY + "/probe").with(csrf()).with(authentication(reader)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(GATEWAY + "/budget").with(csrf()).with(authentication(reader))
                        .contentType("application/json")
                        .content("{\"monthlyLimit\": 10.00, \"version\": 0}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("o teto e o ledger de outra cervejaria não existem para quem pergunta")
    void isolaPorCervejaria() throws Exception {
        var session = login();
        mockMvc.perform(budgetRequest(session, "120.00", currentBudgetVersion(session)))
                .andExpect(status().isOk());
        mockMvc.perform(post(GATEWAY + "/probe").with(csrf()).session(session))
                .andExpect(status().isNotImplemented());

        var other = principal(UUID.randomUUID(),
                Set.of("ai.gateway.read", "ai.gateway.probe", "ai.budget.manage"));
        var status = read(mockMvc.perform(get(GATEWAY).with(authentication(other)))
                .andExpect(status().isOk()));

        // Teto padrão, não os 120 da outra; ledger vazio, não a tentativa da outra.
        org.assertj.core.api.Assertions.assertThat(status.get("budget").get("version").asLong())
                .isZero();
        org.assertj.core.api.Assertions.assertThat(status.get("budget").get("monthlyLimit").decimalValue())
                .isNotEqualByComparingTo("120.00");
        org.assertj.core.api.Assertions.assertThat(status.get("recent").size()).isZero();
    }

    @Test
    @DisplayName("sem cervejaria ativa, nada responde: o gateway é sempre de alguma cervejaria")
    void semCervejariaAtivaNadaResponde() throws Exception {
        var tenantless = principal(null, Set.of("ai.gateway.read", "ai.gateway.probe"));

        mockMvc.perform(get(GATEWAY).with(authentication(tenantless)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(GATEWAY + "/probe").with(csrf()).with(authentication(tenantless)))
                .andExpect(status().isForbidden());
    }

    // --- infraestrutura ---

    /**
     * Lê a versão atual do teto antes de gravar, como qualquer cliente honesto faria.
     *
     * <p>Os testes compartilham o mesmo banco, então cravar "versão 0" acoplaria o resultado à ordem de
     * execução. Ler antes de escrever remove o acoplamento e, de quebra, exercita o fluxo real: a versão
     * que se envia é a que se leu.
     */
    private long currentBudgetVersion(MockHttpSession session) throws Exception {
        return read(mockMvc.perform(get(GATEWAY).session(session)).andExpect(status().isOk()))
                .get("budget").get("version").asLong();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder budgetRequest(
            MockHttpSession session, String limit, long version) {
        return put(GATEWAY + "/budget").with(csrf()).session(session)
                .contentType("application/json")
                .content("{\"monthlyLimit\": " + limit + ", \"version\": " + version + "}");
    }

    private JsonNode read(org.springframework.test.web.servlet.ResultActions actions) throws Exception {
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
