package br.com.brew.brassia.container;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
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
import java.time.Duration;
import java.time.LocalDate;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
 * Empréstimo, prazo, caução, perda e higienização de ponta a ponta (CON-003).
 *
 * <p>O que estes testes fixam: <strong>atrasado é o que não voltou depois do prazo</strong>, a caução
 * registra a decisão e não o dinheiro, e <strong>perda não é baixa</strong> — é o único caminho pelo qual
 * um keg que está na rua sai do inventário.
 */
@SpringBootTest
@Testcontainers
class ContainerLoanIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String BASE = "/api/v1/containers";

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
    void oVasilhameSaiComPrazoEVoltaLiberandoACaucao() throws Exception {
        // A caução registra a DECISÃO, e não o dinheiro: devolvê-la é lançamento financeiro.
        var session = login();
        var keg = registra(session);
        empresta(session, keg, cliente(session), LocalDate.now().plusDays(30), "120.00");

        mockMvc.perform(get(BASE + "/loans").session(session))
                .andExpect(status().isOk())
                // A lista é da casa inteira; o que interessa aqui é a linha deste vasilhame.
                .andExpect(jsonPath("$[?(@.containerId == '" + keg + "')].overdue", contains(false)))
                .andExpect(jsonPath("$[?(@.containerId == '" + keg + "')].daysLate", contains(0)))
                .andExpect(jsonPath("$[?(@.containerId == '" + keg + "')].depositOutcome",
                        contains("HELD")));

        mockMvc.perform(post(BASE + "/" + keg + "/loans/return").session(session).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(BASE + "/" + keg + "/loans").session(session))
                .andExpect(jsonPath("$[0].depositOutcome", is("TO_REFUND")))
                .andExpect(jsonPath("$[0].returnedAt").exists());

        // E some da fila de abertos.
        mockMvc.perform(get(BASE + "/loans").session(session))
                .andExpect(jsonPath("$[?(@.containerId == '" + keg + "')]", empty()));
    }

    @Test
    void atrasadoEOQueNaoVoltouDepoisDoPrazo() throws Exception {
        // Quem devolveu tarde já é história: misturar os dois faria a cobrança do dia ligar para quem já
        // devolveu.
        var session = login();
        var keg = registra(session);
        empresta(session, keg, cliente(session), LocalDate.now(), null);
        // O empréstimo é envelhecido no banco: a API recusa prazo anterior à saída de propósito —
        // um prazo que nasce vencido é engano de digitação, e o teste não pode pedir ao sistema que
        // aceite o que ele existe para recusar.
        jdbc.sql("""
                UPDATE container_loan SET lent_at = now() - interval '10 days',
                    due_on = (now() - interval '5 days')::date
                WHERE container_id = :c
                """).param("c", UUID.fromString(keg)).update();

        mockMvc.perform(get(BASE + "/loans").param("overdueOn", LocalDate.now().toString())
                        .session(session))
                .andExpect(jsonPath("$[?(@.containerId == '" + keg + "')].overdue", contains(true)))
                .andExpect(jsonPath("$[?(@.containerId == '" + keg + "')].daysLate", contains(5)));

        mockMvc.perform(post(BASE + "/" + keg + "/loans/return").session(session).with(csrf()))
                .andExpect(status().isNoContent());

        // Devolvido tarde não é atraso em aberto — mas fica registrado.
        mockMvc.perform(get(BASE + "/loans").param("overdueOn", LocalDate.now().toString())
                        .session(session))
                .andExpect(jsonPath("$[?(@.containerId == '" + keg + "')]", empty()));
        mockMvc.perform(get(BASE + "/" + keg + "/loans").session(session))
                .andExpect(jsonPath("$[0].returnedLate", is(true)));
    }

    @Test
    void oMesmoKegNaoSaiParaDoisClientes() throws Exception {
        // Impossível no mundo, e contabilizaria duas cauções. A garantia é o índice único parcial.
        var session = login();
        var keg = registra(session);
        empresta(session, keg, cliente(session), LocalDate.now().plusDays(30), null);

        mockMvc.perform(post(BASE + "/" + keg + "/loans").session(session).with(csrf())
                        .contentType("application/json")
                        .content(corpoEmprestimo(cliente(session), LocalDate.now().plusDays(30), null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("loan_not_allowed")))
                .andExpect(jsonPath("$.reasonCode", is("already_lent")));
    }

    @Test
    void aPerdaEncerraOEmprestimoReTemACaucaoEBaixaOVasilhame() throws Exception {
        // Perda NÃO é baixa: é o único caminho pelo qual um keg que está na rua sai do inventário, com
        // motivo e alçada crítica.
        var session = login();
        var keg = registra(session);
        empresta(session, keg, cliente(session), LocalDate.now().plusDays(30), "120.00");

        mockMvc.perform(post(BASE + "/" + keg + "/loans/loss").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"reason\":\"o bar fechou e não devolveu\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(BASE + "/" + keg + "/loans").session(session))
                .andExpect(jsonPath("$[0].depositOutcome", is("RETAINED")))
                .andExpect(jsonPath("$[0].lossReason", is("o bar fechou e não devolveu")));

        // O vasilhame saiu do inventário, e o motivo diz que foi perda — e não descarte.
        mockMvc.perform(get(BASE + "/" + keg).session(session))
                .andExpect(jsonPath("$.state", is("RETIRED")))
                .andExpect(jsonPath("$.retirementReason",
                        org.hamcrest.Matchers.containsString("perdido")));
    }

    @Test
    void declararPerdaTemAlcadaPropria() throws Exception {
        // Ela tira um ativo do inventário E retém dinheiro do cliente.
        var session = login();
        var keg = registra(session);
        empresta(session, keg, cliente(session), LocalDate.now().plusDays(30), null);

        mockMvc.perform(post(BASE + "/" + keg + "/loans/loss")
                        .with(authentication(principal(breweryOf(keg),
                                Set.of("container.read", "container.loan.manage"))))
                        .with(csrf()).contentType("application/json")
                        .content("{\"reason\":\"sumiu\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aPerdaPrecisaDeMotivo() throws Exception {
        // "Perdido" sozinho não distingue o bar que fechou do keg roubado do caminhão.
        var session = login();
        var keg = registra(session);
        empresta(session, keg, cliente(session), LocalDate.now().plusDays(30), null);

        mockMvc.perform(post(BASE + "/" + keg + "/loans/loss").session(session).with(csrf())
                        .contentType("application/json").content("{\"reason\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void oEmprestimoSemCaucaoEEstadoLegitimo() throws Exception {
        // Nem toda casa cobra, e obrigar um valor faria alguém digitar 1 real para poder seguir.
        var session = login();
        var keg = registra(session);
        empresta(session, keg, cliente(session), LocalDate.now().plusDays(30), null);

        mockMvc.perform(get(BASE + "/loans").session(session))
                .andExpect(jsonPath("$[?(@.containerId == '" + keg + "')].depositOutcome",
                        contains("HELD")));
        // Ausência de caução é NULO, e não zero: zero somaria no relatório de valores retidos como se
        // houvesse dinheiro parado.
        mockMvc.perform(get(BASE + "/" + keg + "/loans").session(session))
                .andExpect(jsonPath("$[0].depositAmount").doesNotExist());
    }

    @Test
    void oPrazoNaoNasceVencido() throws Exception {
        var session = login();
        var keg = registra(session);

        mockMvc.perform(post(BASE + "/" + keg + "/loans").session(session).with(csrf())
                        .contentType("application/json")
                        .content(corpoEmprestimo(cliente(session), LocalDate.now().minusDays(1), null)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void aHigienizacaoDizOQueFoiFeitoEQuemFez() throws Exception {
        // "Higienizado" sem dizer como é um carimbo, e um carimbo não se audita. A pergunta chega três
        // meses depois: aquele keg foi lavado antes da cerveja que o cliente reclamou?
        var session = login();
        var keg = registra(session);

        mockMvc.perform(post(BASE + "/" + keg + "/sanitations").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"method\":\"soda 2% a 60 °C\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get(BASE + "/" + keg + "/sanitations").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].method", is("soda 2% a 60 °C")))
                .andExpect(jsonPath("$[0].performedBy").exists());

        mockMvc.perform(post(BASE + "/" + keg + "/sanitations").session(session).with(csrf())
                        .contentType("application/json").content("{\"method\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void naoSeDevolveOQueNaoEstaEmprestado() throws Exception {
        var session = login();
        var keg = registra(session);

        mockMvc.perform(post(BASE + "/" + keg + "/loans/return").session(session).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reasonCode", is("no_open_loan")));
    }

    @Test
    void outraCervejariaNaoVeNemEmprestaOVasilhameAlheio() throws Exception {
        var session = login();
        var keg = registra(session);
        var estranho = principal(UUID.randomUUID(),
                Set.of("container.read", "container.loan.manage"));

        mockMvc.perform(get(BASE + "/loans").with(authentication(estranho)))
                .andExpect(jsonPath("$.length()", is(0)));
        mockMvc.perform(post(BASE + "/" + keg + "/loans").with(authentication(estranho)).with(csrf())
                        .contentType("application/json")
                        .content(corpoEmprestimo(UUID.randomUUID().toString(),
                                LocalDate.now().plusDays(30), null)))
                .andExpect(status().isNotFound());
    }

    // --- ações ---

    private void empresta(MockHttpSession session, String keg, String cliente, LocalDate prazo,
            String caucao) throws Exception {
        mockMvc.perform(post(BASE + "/" + keg + "/loans").session(session).with(csrf())
                        .contentType("application/json")
                        .content(corpoEmprestimo(cliente, prazo, caucao)))
                .andExpect(status().isCreated());
    }

    private static String corpoEmprestimo(String cliente, LocalDate prazo, String caucao) {
        var deposito = caucao == null ? ""
                : ",\"depositAmount\":%s,\"depositCurrency\":\"BRL\"".formatted(caucao);
        return """
                {"customerId":"%s","customerName":"Bar do Bruno","dueOn":"%s"%s}
                """.formatted(cliente, prazo, deposito);
    }

    private String cliente(MockHttpSession session) throws Exception {
        var body = mockMvc.perform(post("/api/v1/crm/customers").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"legalName\":\"Bar do Bruno %s\"}"
                                .formatted(UUID.randomUUID().toString().substring(0, 6))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("id").asText();
    }

    private String registra(MockHttpSession session) throws Exception {
        var sfx = UUID.randomUUID().toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT);
        var corpo = mockMvc.perform(post(BASE).session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"code":"KEG-%s","kind":"KEG","nominalCapacityLiters":50,
                                 "ownership":"OWN"}
                                """.formatted(sfx)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(corpo).get("id").asText();
    }

    private UUID breweryOf(String containerId) {
        return jdbc.sql("SELECT brewery_id FROM container WHERE id = :i")
                .param("i", UUID.fromString(containerId)).query(UUID.class).single();
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
