package br.com.brew.brassia.container;

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
 * Identidade e ciclo do contêiner de ponta a ponta (CON-001).
 *
 * <p>O que estes testes fixam: <strong>ler um código identifica e não autoriza</strong>, o que voltou do
 * cliente não está pronto, e a inspeção vencida bloqueia o enchimento.
 */
@SpringBootTest
@Testcontainers
@Import(ScriptedLots.class)
class ContainerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String BASE = "/api/v1/containers";

    @Autowired
    WebApplicationContext context;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    ScriptedLots.Roteiro lotes;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void oConteinerNasceSemInspecaoENaoSeEnche() throws Exception {
        // "Nunca foi inspecionado" é pior que "venceu": tratar a ausência como aprovação deixaria a
        // frota nova inteira fora de controle.
        var session = login();
        var keg = registra(session);

        mockMvc.perform(get(BASE + "/" + keg).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", is("EMPTY")))
                .andExpect(jsonPath("$.fillable", is(false)));

        enche(session, keg, status().isConflict())
                .andExpect(jsonPath("$.code", is("container_not_fillable")))
                .andExpect(jsonPath("$.reasonCode", is("inspection_expired")));

        // E "cheio" deixou de ser um movimento cego: sem dizer qual lote entrou, o vasilhame ficaria
        // cheio de nada, e a genealogia teria um buraco onde o recall olha.
        move(session, keg, "FILLED", status().isConflict())
                .andExpect(jsonPath("$.reasonCode", is("content_required")));
    }

    @Test
    void aInspecaoLiberaOEnchimentoEOCicloNaoPulaEtapas() throws Exception {
        var session = login();
        var keg = registra(session);
        inspeciona(session, keg, Duration.ofDays(365));

        mockMvc.perform(get(BASE + "/" + keg).session(session))
                .andExpect(jsonPath("$.fillable", is(true)));

        // Entregar o que nunca saiu do depósito é engano de operação, e o registro dele viraria uma
        // entrega que ninguém fez.
        move(session, keg, "AT_CUSTOMER", status().isConflict())
                .andExpect(jsonPath("$.code", is("illegal_container_transition")));

        enche(session, keg, status().isCreated());
        move(session, keg, "IN_TRANSIT", status().isNoContent());
        move(session, keg, "AT_CUSTOMER", status().isNoContent());
    }

    @Test
    void oQueVoltouDoClienteNaoEstaPronto() throws Exception {
        // A decisão central da história: derivar a limpeza da chegada encheria um vasilhame que ninguém
        // lavou, e o problema apareceria na boca do cliente.
        var session = login();
        var keg = noCliente(session);

        move(session, keg, "RETURNED", status().isNoContent());
        mockMvc.perform(get(BASE + "/" + keg).session(session))
                .andExpect(jsonPath("$.state", is("RETURNED")))
                .andExpect(jsonPath("$.fillable", is(false)));

        enche(session, keg, status().isConflict())
                .andExpect(jsonPath("$.reasonCode", is("not_ready")));

        // Alguém DIZ que está limpo. É ato explícito, como a liberação do lote pela qualidade.
        move(session, keg, "EMPTY", status().isNoContent());
        mockMvc.perform(get(BASE + "/" + keg).session(session))
                .andExpect(jsonPath("$.fillable", is(true)));
    }

    @Test
    void aInspecaoVencidaBloqueiaOEnchimento() throws Exception {
        // Vaso de pressão sem inspeção em dia é risco físico, e não pendência de papel.
        var session = login();
        var keg = registra(session);
        // Uma inspeção que já venceu: feita há dois anos, válida por um.
        var feita = Instant.now().minus(Duration.ofDays(730));
        mockMvc.perform(post(BASE + "/" + keg + "/inspections").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"performedAt":"%s","validUntil":"%s"}
                                """.formatted(feita, feita.plus(Duration.ofDays(365)))))
                .andExpect(status().isNoContent());

        enche(session, keg, status().isConflict())
                .andExpect(jsonPath("$.reasonCode", is("inspection_expired")));
    }

    @Test
    void lerUmCodigoIdentificaENaoAutoriza() throws Exception {
        // O critério transversal da sprint. Quem escaneou continua precisando de alçada para agir: a
        // etiqueta responde "qual keg é esta", e não "pode mexer".
        var session = login();
        var keg = registra(session);
        etiqueta(session, keg, "QR-CON-001", "QR");

        mockMvc.perform(get(BASE + "/by-identifier").param("value", "QR-CON-001").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(keg)));

        // Com o código na mão e sem alçada de leitura, não abre.
        mockMvc.perform(get(BASE + "/by-identifier").param("value", "QR-CON-001")
                        .with(authentication(principal(UUID.randomUUID(), Set.of()))))
                .andExpect(status().isForbidden());

        // E com alçada de leitura de OUTRA cervejaria, o código não revela nada.
        mockMvc.perform(get(BASE + "/by-identifier").param("value", "QR-CON-001")
                        .with(authentication(principal(UUID.randomUUID(), Set.of("container.read")))))
                .andExpect(status().isNotFound());
    }

    @Test
    void aEtiquetaViveEmUmConteinerSo() throws Exception {
        // A garantia é o índice único parcial: duas telas colando o mesmo adesivo em kegs diferentes
        // passariam por qualquer checagem prévia e deixariam a leitura ambígua para sempre.
        var session = login();
        var primeiro = registra(session);
        var segundo = registra(session);
        etiqueta(session, primeiro, "QR-DUP", "QR");

        mockMvc.perform(post(BASE + "/" + segundo + "/identifiers").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"value\":\"QR-DUP\",\"technology\":\"QR\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("identifier_in_use")));
    }

    @Test
    void aEtiquetaAposentadaNaoResolveEOValorPodeSerReaproveitado() throws Exception {
        // Aposentar não apaga — a etiqueta continua explicando leituras antigas —, mas ela deixa de
        // apontar: senão uma entrega de seis meses atrás passaria a resolver para outro keg.
        var session = login();
        var antigo = registra(session);
        var novo = registra(session);
        var etiquetaId = etiqueta(session, antigo, "QR-MOVEL", "QR");

        mockMvc.perform(post(BASE + "/identifiers/" + etiquetaId + "/retire").session(session)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(BASE + "/by-identifier").param("value", "QR-MOVEL").session(session))
                .andExpect(status().isNotFound());

        // O mesmo valor pode viver de novo, agora noutro contêiner: o índice único é PARCIAL.
        etiqueta(session, novo, "QR-MOVEL", "QR");
        mockMvc.perform(get(BASE + "/by-identifier").param("value", "QR-MOVEL").session(session))
                .andExpect(jsonPath("$.id", is(novo)));

        // E a etiqueta velha continua na lista do keg antigo, com a data de aposentadoria.
        mockMvc.perform(get(BASE + "/" + antigo + "/identifiers").session(session))
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].retiredAt").exists());
    }

    @Test
    void oAvariadoNaoRecebeCervejaEAManutencaoODevolve() throws Exception {
        var session = login();
        var keg = registra(session);
        inspeciona(session, keg, Duration.ofDays(365));

        mockMvc.perform(post(BASE + "/" + keg + "/condition").session(session).with(csrf())
                        .contentType("application/json").content("{\"condemned\":false}"))
                .andExpect(status().isNoContent());

        enche(session, keg, status().isConflict())
                .andExpect(jsonPath("$.reasonCode", is("damaged")));

        move(session, keg, "IN_MAINTENANCE", status().isNoContent());
        // "Vazio" vindo da oficina: o estado atual diz qual das duas transições é, e a API não pergunta.
        move(session, keg, "EMPTY", status().isNoContent());

        mockMvc.perform(get(BASE + "/" + keg).session(session))
                .andExpect(jsonPath("$.condition", is("GOOD")))
                .andExpect(jsonPath("$.fillable", is(true)));
    }

    @Test
    void naoSeDaBaixaNoQueEstaComOCliente() throws Exception {
        // O vasilhame que não voltou é PERDA, que é outro fato e tem outro dono (CON-003).
        var session = login();
        var keg = noCliente(session);

        mockMvc.perform(post(BASE + "/" + keg + "/retire").session(session).with(csrf())
                        .contentType("application/json").content("{\"reason\":\"sumiu\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("illegal_container_transition")));
    }

    @Test
    void aBaixaETerminalEGuardaOMotivo() throws Exception {
        var session = login();
        var keg = registra(session);

        mockMvc.perform(post(BASE + "/" + keg + "/retire").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"reason\":\"furo na costura, sem recuperação\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(BASE + "/" + keg).session(session))
                .andExpect(jsonPath("$.state", is("RETIRED")))
                .andExpect(jsonPath("$.retirementReason", is("furo na costura, sem recuperação")));

        // Baixado é histórico, e não estoque: 409 com motivo, e não 500.
        enche(session, keg, status().isConflict())
                .andExpect(jsonPath("$.code", is("container_retired")));
    }

    @Test
    void aBaixaEAInspecaoTemAlcadaPropria() throws Exception {
        // Liberar um vaso de pressão para uso é atestado técnico, e dar baixa tira um ativo do
        // inventário. Nenhuma das duas é operação de rotina.
        var session = login();
        var keg = registra(session);
        var operador = principal(breweryOf(keg), Set.of("container.read", "container.manage"));

        mockMvc.perform(post(BASE + "/" + keg + "/inspections").with(authentication(operador))
                        .with(csrf()).contentType("application/json")
                        .content("""
                                {"performedAt":"%s","validUntil":"%s"}
                                """.formatted(Instant.now(), Instant.now().plus(Duration.ofDays(365)))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(BASE + "/" + keg + "/retire").with(authentication(operador)).with(csrf())
                        .contentType("application/json").content("{\"reason\":\"qualquer\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void outraCervejariaNaoVeNemMoveOVasilhameAlheio() throws Exception {
        var session = login();
        var keg = registra(session);
        var estranho = principal(UUID.randomUUID(),
                Set.of("container.read", "container.manage", "container.inspect"));

        mockMvc.perform(get(BASE + "/" + keg).with(authentication(estranho)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(BASE + "/" + keg + "/moves").with(authentication(estranho)).with(csrf())
                        .contentType("application/json").content("{\"to\":\"FILLED\"}"))
                .andExpect(status().isNotFound());
    }

    // --- cenário ---

    private String noCliente(MockHttpSession session) throws Exception {
        var keg = registra(session);
        inspeciona(session, keg, Duration.ofDays(365));
        enche(session, keg, status().isCreated());
        move(session, keg, "IN_TRANSIT", status().isNoContent());
        move(session, keg, "AT_CUSTOMER", status().isNoContent());
        return keg;
    }

    /** Encher agora é dizer qual lote entrou (CON-002). */
    private org.springframework.test.web.servlet.ResultActions enche(MockHttpSession session,
            String keg, org.springframework.test.web.servlet.ResultMatcher esperado) throws Exception {
        var lote = lotes.recemEnvasado("L-" + UUID.randomUUID().toString().substring(0, 6));
        return mockMvc.perform(post(BASE + "/" + keg + "/fills").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"finishedLotId\":\"%s\",\"volumeLiters\":50}".formatted(lote)))
                .andExpect(esperado);
    }

    private org.springframework.test.web.servlet.ResultActions move(MockHttpSession session, String keg,
            String to, org.springframework.test.web.servlet.ResultMatcher esperado) throws Exception {
        return mockMvc.perform(post(BASE + "/" + keg + "/moves").session(session).with(csrf())
                        .contentType("application/json").content("{\"to\":\"%s\"}".formatted(to)))
                .andExpect(esperado);
    }

    private void inspeciona(MockHttpSession session, String keg, Duration validade) throws Exception {
        var agora = Instant.now();
        mockMvc.perform(post(BASE + "/" + keg + "/inspections").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"performedAt":"%s","validUntil":"%s"}
                                """.formatted(agora, agora.plus(validade))))
                .andExpect(status().isNoContent());
    }

    private String etiqueta(MockHttpSession session, String keg, String valor, String tech)
            throws Exception {
        var corpo = mockMvc.perform(post(BASE + "/" + keg + "/identifiers").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"value\":\"%s\",\"technology\":\"%s\"}".formatted(valor, tech)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(corpo).get("id").asText();
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
