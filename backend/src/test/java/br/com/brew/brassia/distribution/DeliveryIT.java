package br.com.brew.brassia.distribution;

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
 * Prova de entrega e coleta de ponta a ponta (LOG-002).
 *
 * <p>O que estes testes fixam: <strong>a prova não se edita</strong> — corrige-se por evento novo, e as
 * duas ficam —, a mídia só existe com consentimento, e a coordenada é gravada arredondada.
 */
@SpringBootTest
@Testcontainers
@Import(LoadIT.ScriptedContainers.class)
class DeliveryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String BASE = "/api/v1/distribution/loads";
    private static final String DIST = "/api/v1/distribution";

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
    void aEntregaSoSeRegistraDepoisDeACargaSair() throws Exception {
        // Uma entrega registrada antes da saída é um registro do que não aconteceu.
        var session = login();
        var cena = cenaNaRua(session, false);

        mockMvc.perform(post(prova(cena)).session(session).with(csrf())
                        .contentType("application/json").content(corpoEntrega(cena.keg())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("delivery_not_recordable")))
                .andExpect(jsonPath("$.reasonCode", is("load_not_on_the_road")));
    }

    @Test
    void aMesmaParadaNaoSeRegistraDuasVezes() throws Exception {
        // O duplo clique do celular no meio da rua viraria duas entregas para o mesmo cliente.
        var session = login();
        var cena = cenaNaRua(session, true);
        registra(session, cena, corpoEntrega(cena.keg()));

        mockMvc.perform(post(prova(cena)).session(session).with(csrf())
                        .contentType("application/json").content(corpoEntrega(cena.keg())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reasonCode", is("already_recorded")));
    }

    @Test
    void aProvaNaoSeEditaESeCorrigePorEventoNovo() throws Exception {
        // O critério transversal da sprint. Uma prova reescrita parece original e diz outra coisa.
        var session = login();
        var cena = cenaNaRua(session, true);
        registra(session, cena, corpoEntrega(cena.keg()));

        mockMvc.perform(post(DIST + "/stops/" + cena.stopId() + "/proof/correction").session(session)
                        .with(csrf()).contentType("application/json")
                        .content("""
                                {"outcome":"REFUSED","delivered":[],"collected":[],
                                 "reason":"o cliente recusou; marquei errado na pressa"}
                                """))
                .andExpect(status().isCreated());

        // As DUAS ficam, e a correção aponta para a original.
        mockMvc.perform(get(DIST + "/stops/" + cena.stopId() + "/proof").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(2)))
                .andExpect(jsonPath("$[0].outcome", is("DELIVERED")))
                .andExpect(jsonPath("$[0].correctsProofId").doesNotExist())
                .andExpect(jsonPath("$[1].outcome", is("REFUSED")))
                .andExpect(jsonPath("$[1].correctsProofId").exists());
    }

    @Test
    void naoHaComoApagarUmaProva() throws Exception {
        // A ausência do verbo é a regra: não existe DELETE nem PUT nesta superfície.
        var session = login();
        var cena = cenaNaRua(session, true);
        registra(session, cena, corpoEntrega(cena.keg()));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete(DIST + "/stops/" + cena.stopId() + "/proof").session(session)
                        .with(csrf()))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void corrigirTemAlcadaPropria() throws Exception {
        // Corrigir mexe no que já foi dado como fato: é ato que precisa de nome e trilha.
        var session = login();
        var cena = cenaNaRua(session, true);
        registra(session, cena, corpoEntrega(cena.keg()));

        mockMvc.perform(post(DIST + "/stops/" + cena.stopId() + "/proof/correction")
                        .with(authentication(principal(breweryOf(cena.loadId()),
                                Set.of("distribution.delivery.record", "distribution.load.read"))))
                        .with(csrf()).contentType("application/json")
                        .content("""
                                {"outcome":"REFUSED","delivered":[],"collected":[],"reason":"tentando"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void aEntregaAconteceSemAssinatura() throws Exception {
        // Recusar consentimento não trava a operação: o cliente que não quer assinar continua
        // recebendo a cerveja.
        var session = login();
        var cena = cenaNaRua(session, true);
        registra(session, cena, corpoEntrega(cena.keg()));

        mockMvc.perform(get(DIST + "/stops/" + cena.stopId() + "/proof").session(session))
                .andExpect(jsonPath("$[0].mediaKind").doesNotExist())
                .andExpect(jsonPath("$[0].outcome", is("DELIVERED")));
    }

    @Test
    void aAssinaturaSoEntraComQuemConsentiuEParaQue() throws Exception {
        // O consentimento não é uma caixinha: é o que autoriza guardar, e sem finalidade vira cheque em
        // branco. A chave do arquivo NÃO sai na listagem.
        var session = login();
        var cena = cenaNaRua(session, true);

        registra(session, cena, """
                {"outcome":"DELIVERED","delivered":["%s"],"collected":[],
                 "signatureConsent":{"kind":"SIGNATURE","storageKey":"s3://provas/1",
                                     "consentedByName":"Bruno","purpose":"comprovar a entrega"}}
                """.formatted(cena.keg()));

        var corpo = mockMvc.perform(get(DIST + "/stops/" + cena.stopId() + "/proof").session(session))
                .andExpect(jsonPath("$[0].mediaKind", is("SIGNATURE")))
                .andExpect(jsonPath("$[0].mediaPurpose", is("comprovar a entrega")))
                .andExpect(jsonPath("$[0].consentedByName", is("Bruno")))
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(corpo).doesNotContain("s3://provas/1");
    }

    @Test
    void aAssinaturaSemFinalidadeNaoEntra() throws Exception {
        var session = login();
        var cena = cenaNaRua(session, true);

        mockMvc.perform(post(prova(cena)).session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"outcome":"DELIVERED","delivered":["%s"],"collected":[],
                                 "signatureConsent":{"kind":"SIGNATURE","storageKey":"s3://x",
                                                     "consentedByName":"Bruno"}}
                                """.formatted(cena.keg())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aCoordenadaEGravadaArredondada() throws Exception {
        // A coordenada cheia do celular do entregador, parada a parada, todo dia, é um rastro de
        // movimentação de uma pessoa. A precisão está no tipo da coluna, e não numa convenção.
        var session = login();
        var cena = cenaNaRua(session, true);

        registra(session, cena, """
                {"outcome":"DELIVERED","delivered":["%s"],"collected":[],
                 "latitude":-23.5614321,"longitude":-46.6565987}
                """.formatted(cena.keg()));

        var lat = jdbc.sql("SELECT latitude FROM distribution_proof WHERE stop_id = :s")
                .param("s", UUID.fromString(cena.stopId())).query(java.math.BigDecimal.class).single();
        org.assertj.core.api.Assertions.assertThat(lat.scale()).isEqualTo(3);
        org.assertj.core.api.Assertions.assertThat(lat).isEqualByComparingTo("-23.561");
    }

    @Test
    void aEntregaMoveOVasilhameEACOletaOTrazSujo() throws Exception {
        // É isso que faz o estoque contar certo sem ninguém digitar duas vezes.
        var session = login();
        var cena = cenaNaRua(session, true);
        registra(session, cena, corpoEntrega(cena.keg()));

        mockMvc.perform(get(DIST + "/stops/" + cena.stopId() + "/proof").session(session))
                .andExpect(jsonPath("$[0].delivered.length()", is(1)));

        // A saída põe o keg na rua — e é isto que fecha o DEB-LOG-001: um vasilhame em trânsito não está
        // mais no depósito, e o ciclo passa a impedir que ele entre numa segunda carga.
        org.assertj.core.api.Assertions.assertThat(movimentos.chamadas)
                .contains("dispatch:" + cena.keg())
                .contains("deliver:" + cena.keg());
    }

    @Test
    void naoSeEntregaOQueNaoEstavaNaParada() throws Exception {
        // Entregar o que não saiu do depósito faria o estoque perder a conta.
        var session = login();
        var cena = cenaNaRua(session, true);

        mockMvc.perform(post(prova(cena)).session(session).with(csrf())
                        .contentType("application/json")
                        .content(corpoEntrega(UUID.randomUUID().toString())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reasonCode", is("not_in_stop")));
    }

    @Test
    void aNaoEntregaPrecisaDoMotivo() throws Exception {
        // "Recusado" sozinho não diz se foi preço, avaria ou pedido errado.
        var session = login();
        var cena = cenaNaRua(session, true);

        mockMvc.perform(post(prova(cena)).session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"outcome\":\"REFUSED\",\"delivered\":[],\"collected\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void coletarSemEntregarEEstadoLegitimo() throws Exception {
        // O motorista recolhe vazios num bar onde não deixou nada.
        var session = login();
        var cena = cenaNaRua(session, true);
        var vazio = UUID.randomUUID();

        registra(session, cena, """
                {"outcome":"ABSENT","delivered":[],"collected":["%s"],
                 "note":"ninguém para receber, mas os vazios estavam na calçada"}
                """.formatted(vazio));

        mockMvc.perform(get(DIST + "/stops/" + cena.stopId() + "/proof").session(session))
                .andExpect(jsonPath("$[0].delivered.length()", is(0)))
                .andExpect(jsonPath("$[0].collected.length()", is(1)));

        // O que volta do cliente volta SUJO, e o período do lote se fecha aí (CON-001/CON-002).
        org.assertj.core.api.Assertions.assertThat(movimentos.chamadas)
                .contains("collect:" + vazio);
    }

    @Test
    void outraCervejariaNaoLeAsProvasAlheias() throws Exception {
        var session = login();
        var cena = cenaNaRua(session, true);
        registra(session, cena, corpoEntrega(cena.keg()));

        mockMvc.perform(get(DIST + "/stops/" + cena.stopId() + "/proof")
                        .with(authentication(principal(UUID.randomUUID(),
                                Set.of("distribution.load.read")))))
                .andExpect(jsonPath("$.length()", is(0)));
    }

    // --- cenário ---

    private record Cena(String loadId, String stopId, String keg) {}

    private String prova(Cena cena) {
        return BASE + "/" + cena.loadId() + "/stops/" + cena.stopId() + "/proof";
    }

    private static String corpoEntrega(String keg) {
        return """
                {"outcome":"DELIVERED","delivered":["%s"],"collected":[]}
                """.formatted(keg);
    }

    private void registra(MockHttpSession session, Cena cena, String corpo) throws Exception {
        mockMvc.perform(post(prova(cena)).session(session).with(csrf())
                        .contentType("application/json").content(corpo))
                .andExpect(status().isCreated());
    }

    /** Uma carga montada, conferida e — quando pedido — já na rua. */
    private Cena cenaNaRua(MockHttpSession session, boolean saiu) throws Exception {
        var carga = planejaCom(session, "1000");
        var parada = adicionaParada(session, carga, cliente(session), 1);
        var keg = vasilhames.pronto("KEG-" + UUID.randomUUID(), new java.math.BigDecimal("50"));
        poeNaCarga(session, carga, parada, keg);
        atribui(session, carga);
        mockMvc.perform(post(BASE + "/" + carga + "/release")
                        .with(authentication(conferente(breweryOf(carga)))).with(csrf()))
                .andExpect(status().isNoContent());
        if (saiu) {
            mockMvc.perform(post(BASE + "/" + carga + "/depart").session(session).with(csrf()))
                    .andExpect(status().isNoContent());
        }
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
