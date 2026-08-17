package br.com.brew.brassia.container;

import br.com.brew.brassia.support.BrewScenario;
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
 * Conteúdo e posição do vasilhame de ponta a ponta (CON-002).
 *
 * <p>O que estes testes fixam: <strong>esvaziar não apaga o que esteve dentro</strong>, o keg responde
 * pelo lote do dia certo, e encher precede liberar.
 */
@SpringBootTest
@Testcontainers
class ContainerFillIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String BASE = "/api/v1/containers";

    @Autowired
    WebApplicationContext context;

    @Autowired
    JdbcClient jdbc;

    BrewScenario cenario;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
        cenario = new BrewScenario(mockMvc);
    }

    @Test
    void esvaziarNaoApagaOQueEsteveDentro() throws Exception {
        // A decisão central: um campo "lote atual" sobrescrito responderia "o que está dentro agora" e
        // perderia "o que estava dentro em 12 de março" — que é a pergunta do recall.
        var session = login();
        var keg = pronto(session);
        var lote = cenario.finishedLot(session);
        enche(session, keg, UUID.fromString(lote.id()), 50, status().isCreated());

        mockMvc.perform(post(BASE + "/" + keg + "/fills/empty").session(session).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(BASE + "/" + keg + "/fills").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                // O código é o que o envase gerou: a fixture constrói um lote de verdade, e o teste
                // deixou de afirmar um rótulo inventado (DEB-CON-001).
                .andExpect(jsonPath("$[0].lotCode", is(lote.code())))
                .andExpect(jsonPath("$[0].current", is(false)))
                .andExpect(jsonPath("$[0].emptiedAt").exists());
    }

    @Test
    void oKegResponsePeloLoteDoDiaCerto() throws Exception {
        // O histórico é o que permite a um vasilhame que vive anos dizer o que carregou em cada período.
        var session = login();
        var keg = pronto(session);

        var primeiro = cenario.finishedLot(session);
        var segundo = cenario.finishedLot(session);
        enche(session, keg, UUID.fromString(primeiro.id()), 50, status().isCreated());
        esvazia(session, keg);
        volta(session, keg);
        enche(session, keg, UUID.fromString(segundo.id()), 50, status().isCreated());

        mockMvc.perform(get(BASE + "/" + keg + "/fills").session(session))
                .andExpect(jsonPath("$.length()", is(2)))
                // Do mais recente para o mais antigo, e o primeiro é o que está dentro agora.
                .andExpect(jsonPath("$[0].lotCode", is(segundo.code())))
                .andExpect(jsonPath("$[0].current", is(true)))
                .andExpect(jsonPath("$[1].lotCode", is(primeiro.code())))
                .andExpect(jsonPath("$[1].current", is(false)));
    }

    @Test
    void doisLotesNaoConvivemNoMesmoVasilhame() throws Exception {
        // Seria mistura sem registro, e o recall não saberia o que recolher. A garantia é o índice
        // parcial: a checagem prévia não sobrevive a duas telas enchendo o mesmo keg ao mesmo tempo.
        var session = login();
        var keg = pronto(session);
        enche(session, keg, loteReal(session), 50, status().isCreated());

        enche(session, keg, loteReal(session), 50, status().isConflict())
                .andExpect(jsonPath("$.code", is("container_not_fillable")));
    }

    @Test
    void encherPrecedeLiberar() throws Exception {
        // Kegs são enchidos na produção, antes de a qualidade assinar. Exigir a liberação aqui impediria
        // a operação real de acontecer — quem exige a assinatura é a saída da casa.
        var session = login();
        var keg = pronto(session);
        var naoLiberado = loteReal(session);

        enche(session, keg, naoLiberado, 50, status().isCreated());
    }

    @Test
    void loteVencidoOuEmQuarentenaNaoEntraNoVasilhame() throws Exception {
        // Estes dois são fato consumado no momento do envase, e não pendência que se resolve depois.
        var session = login();
        var vencido = loteVencido(session);
        var quarentena = loteEmQuarentena(session);

        enche(session, pronto(session), vencido, 50, status().isConflict())
                .andExpect(jsonPath("$.code", is("fill_not_allowed")))
                .andExpect(jsonPath("$.reasonCode", is("expired")));

        enche(session, pronto(session), quarentena, 50, status().isConflict())
                .andExpect(jsonPath("$.reasonCode", is("quarantined")));
    }

    @Test
    void oVolumeNaoPodeExcederOVasilhame() throws Exception {
        // Cinquenta litros num keg de cinquenta é o limite; sessenta é erro de digitação que só
        // apareceria na conta do lote.
        var session = login();
        var keg = pronto(session);

        enche(session, keg, loteReal(session), 60, status().isConflict())
                .andExpect(jsonPath("$.reasonCode", is("over_capacity")));
    }

    @Test
    void oQueVoltaDoClienteVoltaVazio() throws Exception {
        // O período do lote se fecha na coleta, sem ninguém precisar lembrar de esvaziar — e fechar não
        // apaga: o intervalo continua ligando este keg àquele lote.
        var session = login();
        var keg = pronto(session);
        var lote = cenario.finishedLot(session);
        enche(session, keg, UUID.fromString(lote.id()), 50, status().isCreated());
        move(session, keg, "IN_TRANSIT");
        move(session, keg, "AT_CUSTOMER");
        move(session, keg, "RETURNED");

        mockMvc.perform(get(BASE + "/" + keg + "/fills").session(session))
                .andExpect(jsonPath("$[0].current", is(false)))
                .andExpect(jsonPath("$[0].lotCode", is(lote.code())));
    }

    @Test
    void aPosicaoAcompanhaOCicloSemNinguemDigitar() throws Exception {
        // O registro que depende de alguém lembrar é o registro que falta justamente no dia em que o keg
        // some.
        var session = login();
        var keg = pronto(session);
        enche(session, keg, loteReal(session), 50, status().isCreated());
        move(session, keg, "IN_TRANSIT");
        move(session, keg, "AT_CUSTOMER");

        mockMvc.perform(get(BASE + "/" + keg + "/locations").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(2)))
                .andExpect(jsonPath("$[0].kind", is("CUSTOMER")))
                .andExpect(jsonPath("$[1].kind", is("IN_TRANSIT")));
    }

    @Test
    void aPosicaoTambemSeRegistraAMao() throws Exception {
        // O ciclo cobre a rua e o cliente, e não a troca de depósito.
        var session = login();
        var keg = pronto(session);

        mockMvc.perform(post(BASE + "/" + keg + "/locations").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"kind\":\"WAREHOUSE\",\"place\":\"Depósito 2\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(BASE + "/" + keg + "/locations").session(session))
                .andExpect(jsonPath("$[0].place", is("Depósito 2")));
    }

    @Test
    void oEnchimentoEntraNaGenealogia() throws Exception {
        // O contêiner é NÓ, e não atributo do lote: o mesmo keg carrega um lote em março e outro em
        // abril, e o recall precisa alcançar o vasilhame do período certo.
        var session = login();
        var keg = pronto(session);
        enche(session, keg, loteReal(session), 50, status().isCreated());

        // A consulta parte do VASILHAME, que é o caminho de um recall que começa numa reclamação de
        // bar: "este keg estava servindo o quê?".
        mockMvc.perform(get("/api/v1/traceability/genealogy")
                        .param("nodeType", "CONTAINER").param("nodeId", keg)
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.edges[?(@.kind == 'envase em vasilhame')]").isNotEmpty())
                // E o nó do vasilhame vem com o código, e não só com o identificador: um grafo de UUIDs
                // não se lê às duas da manhã, que é quando um recall acontece.
                .andExpect(jsonPath("$.root.label").isNotEmpty())
                .andExpect(jsonPath("$.nodes[?(@.type == 'FINISHED_LOT')]").isNotEmpty());
    }

    @Test
    void outraCervejariaNaoLeOConteudoAlheio() throws Exception {
        var session = login();
        var keg = pronto(session);
        enche(session, keg, loteReal(session), 50, status().isCreated());

        mockMvc.perform(get(BASE + "/" + keg + "/fills")
                        .with(authentication(principal(UUID.randomUUID(), Set.of("container.read")))))
                .andExpect(status().isNotFound());
    }

    // --- ações ---

    private org.springframework.test.web.servlet.ResultActions enche(MockHttpSession session,
            String keg, UUID lote, int litros,
            org.springframework.test.web.servlet.ResultMatcher esperado) throws Exception {
        return mockMvc.perform(post(BASE + "/" + keg + "/fills").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"finishedLotId\":\"%s\",\"volumeLiters\":%d}"
                                .formatted(lote, litros)))
                .andExpect(esperado);
    }

    private void esvazia(MockHttpSession session, String keg) throws Exception {
        mockMvc.perform(post(BASE + "/" + keg + "/fills/empty").session(session).with(csrf()))
                .andExpect(status().isNoContent());
    }

    /** Devolve o vasilhame ao estado vazio depois de um esvaziamento manual. */
    private void volta(MockHttpSession session, String keg) throws Exception {
        move(session, keg, "IN_TRANSIT");
        move(session, keg, "AT_CUSTOMER");
        move(session, keg, "RETURNED");
        move(session, keg, "EMPTY");
    }

    private void move(MockHttpSession session, String keg, String to) throws Exception {
        mockMvc.perform(post(BASE + "/" + keg + "/moves").session(session).with(csrf())
                        .contentType("application/json").content("{\"to\":\"%s\"}".formatted(to)))
                .andExpect(status().isNoContent());
    }

    /** Um keg cadastrado e inspecionado: pronto para receber cerveja. */
    private String pronto(MockHttpSession session) throws Exception {
        var keg = registra(session);
        var agora = Instant.now();
        mockMvc.perform(post(BASE + "/" + keg + "/inspections").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"performedAt":"%s","validUntil":"%s"}
                                """.formatted(agora, agora.plus(Duration.ofDays(365)))))
                .andExpect(status().isNoContent());
        return keg;
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

    /**
     * Um lote de produto acabado <strong>de verdade</strong>, pela fixture compartilhada (DEB-CON-001).
     *
     * <p>Aqui morava um dublê de {@code SellableLotLookup}: montar o lote custava as mil linhas do
     * {@code PackagingRunIT}, e o teste ficava verde contra o contrato que supúnhamos. Com a
     * {@link BrewScenario} o custo caiu para uma linha, e o que se exercita é a composição real das
     * condições de venda.
     */
    private UUID loteReal(MockHttpSession session) throws Exception {
        return UUID.fromString(cenario.finishedLot(session).id());
    }

    /**
     * Um lote cuja validade já passou.
     *
     * <p>Envelhecido no banco: a data de envase é do dia, e a API não oferece — de propósito — um jeito
     * de envasar no passado. Aqui o que se testa é a reação do vasilhame ao impedimento, e não a
     * passagem do tempo.
     */
    private UUID loteVencido(MockHttpSession session) throws Exception {
        // LIBERADO e vencido: a ordem dos impedimentos põe a falta de liberação antes da validade, e o
        // enchimento aceita lote não liberado de propósito. Sem liberar, o teste mediria outra coisa.
        var lote = cenario.sellableLot(session);
        // A validade que VALE é o override, que é data absoluta — envelhecer a data de envase não
        // mexeria nela. É a mesma lição do link de compartilhamento: envelhecer o campo certo.
        jdbc.sql("""
                UPDATE packaging_freshness SET override_best_before = current_date - 1
                WHERE plan_id = :p
                """)
                .param("p", UUID.fromString(lote.planId())).update();
        return UUID.fromString(lote.id());
    }

    /** Um lote realmente em quarentena, aberta pelo endpoint que a operação usa. */
    private UUID loteEmQuarentena(MockHttpSession session) throws Exception {
        var lote = cenario.finishedLot(session);
        cenario.recordFreshness(session, lote.planId());
        mockMvc.perform(post("/api/v1/traceability/quarantines").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"nodeType\":\"BATCH\",\"nodeId\":\"" + lote.batchId()
                                + "\",\"reason\":\"investigação aberta no teste\"}"))
                .andExpect(status().isCreated());
        return UUID.fromString(lote.id());
    }
}
