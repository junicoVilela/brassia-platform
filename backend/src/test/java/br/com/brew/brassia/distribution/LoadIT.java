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

import br.com.brew.brassia.container.ContainerMovementCommands;
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
 * Carga, roteiro e conferência de ponta a ponta (LOG-001).
 *
 * <p>O que estes testes fixam: <strong>quem montou não libera</strong>, a carga liberada congela, e
 * <strong>não sai da casa o keg cujo conteúdo a qualidade não liberou</strong> — a promessa que a CON-002
 * deixou em aberto.
 */
@SpringBootTest
@Testcontainers
@Import(LoadIT.ScriptedContainers.class)
class LoadIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String BASE = "/api/v1/distribution/loads";

    @Autowired
    WebApplicationContext context;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    Vasilhames vasilhames;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void quemMontouNaoLibera() throws Exception {
        // A decisão central: a conferência serve para encontrar o erro de quem montou, e quem montou
        // relê o próprio trabalho enxergando o que quis colocar.
        var session = login();
        var carga = cargaPronta(session);

        mockMvc.perform(post(BASE + "/" + carga + "/release").session(session).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("separation_of_duties")));

        // Outra pessoa, com a alçada própria de conferir, libera.
        mockMvc.perform(post(BASE + "/" + carga + "/release")
                        .with(authentication(conferente(breweryOf(carga)))).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(BASE + "/" + carga).session(session))
                .andExpect(jsonPath("$.status", is("RELEASED")))
                .andExpect(jsonPath("$.releasedBy").exists())
                .andExpect(jsonPath("$.frozen", is(true)));
    }

    @Test
    void aAlcadaDeConferirESeparadaDaDeMontar() throws Exception {
        // Só a regra do agregado seria contornável dando as duas permissões a todo mundo; só a alçada
        // seria contornável por quem tem as duas. Juntas, exigem duas pessoas de verdade.
        var session = login();
        var carga = cargaPronta(session);

        mockMvc.perform(post(BASE + "/" + carga + "/release")
                        .with(authentication(principal(breweryOf(carga),
                                Set.of("distribution.load.plan", "distribution.load.read"))))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void aCargaLiberadaCongela() throws Exception {
        // Acrescentar um keg numa carga já conferida desfaz a conferência sem ninguém perceber.
        var session = login();
        var carga = cargaPronta(session);
        libera(carga);

        mockMvc.perform(post(BASE + "/" + carga + "/stops").session(session).with(csrf())
                        .contentType("application/json")
                        .content(corpoParada(cliente(session), 2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("illegal_load_transition")));
    }

    @Test
    void reabrirDerrubaAConferencia() throws Exception {
        // Manter a conferência de pé depois de a carga mudar seria pior que não ter conferência.
        var session = login();
        var carga = cargaPronta(session);
        libera(carga);

        mockMvc.perform(post(BASE + "/" + carga + "/reopen").session(session).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(BASE + "/" + carga).session(session))
                .andExpect(jsonPath("$.status", is("PLANNED")))
                .andExpect(jsonPath("$.releasedBy").doesNotExist())
                .andExpect(jsonPath("$.frozen", is(false)));
    }

    @Test
    void naoSaiDaCasaOKegQueAQualidadeNaoLiberou() throws Exception {
        // A promessa que a CON-002 deixou em aberto: encher precede liberar, e quem exige a assinatura
        // da qualidade é a saída.
        var session = login();
        var carga = planeja(session);
        var parada = adicionaParada(session, carga, cliente(session), 1);
        var keg = vasilhames.bloqueado("KEG-NL", "not_released", "Falta a liberação da qualidade.");

        mockMvc.perform(post(BASE + "/" + carga + "/stops/" + parada + "/containers")
                        .session(session).with(csrf()).contentType("application/json")
                        .content("{\"containerId\":\"%s\"}".formatted(keg)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("container_not_shippable")))
                .andExpect(jsonPath("$.reasonCode", is("not_released")));
    }

    @Test
    void oLoteQueEntraEmQuarentenaDepoisDeMontadoBarraALiberacao() throws Exception {
        // A carga é revalidada inteira antes de sair: entre montar e liberar pode ter passado um dia.
        // Confiar na checagem da montagem seria confiar num retrato de ontem.
        var session = login();
        var carga = planeja(session);
        var parada = adicionaParada(session, carga, cliente(session), 1);
        var keg = vasilhames.pronto("KEG-Q", new BigDecimal("50"));
        poeNaCarga(session, carga, parada, keg);
        atribui(session, carga);

        vasilhames.bloqueia(keg, "quarantined", "O lote entrou em quarentena.");

        mockMvc.perform(post(BASE + "/" + carga + "/release")
                        .with(authentication(conferente(breweryOf(carga)))).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reasonCode", is("quarantined")));
    }

    @Test
    void oVasilhameVazioNaoEntraNaCarga() throws Exception {
        // Carga é o que sai cheio; um keg vazio na rota é engano de leitura de etiqueta.
        var session = login();
        var carga = planeja(session);
        var parada = adicionaParada(session, carga, cliente(session), 1);
        var keg = vasilhames.bloqueado("KEG-V", "container_empty", "O vasilhame está vazio.");

        mockMvc.perform(post(BASE + "/" + carga + "/stops/" + parada + "/containers")
                        .session(session).with(csrf()).contentType("application/json")
                        .content("{\"containerId\":\"%s\"}".formatted(keg)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reasonCode", is("container_empty")));
    }

    @Test
    void aCapacidadeDizQuantoPassou() throws Exception {
        // "Excedeu a capacidade" manda o operador tirar itens no chute até caber.
        var session = login();
        var carga = planejaCom(session, "60");
        var parada = adicionaParada(session, carga, cliente(session), 1);
        poeNaCarga(session, carga, parada, vasilhames.pronto("KEG-1", new BigDecimal("50")));

        mockMvc.perform(post(BASE + "/" + carga + "/stops/" + parada + "/containers")
                        .session(session).with(csrf()).contentType("application/json")
                        .content("{\"containerId\":\"%s\"}"
                                .formatted(vasilhames.pronto("KEG-2", new BigDecimal("50")))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("load_capacity_exceeded")))
                // Quanto passou, e não só que passou.
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("40")))
                .andExpect(jsonPath("$.excessLiters").exists());
    }

    @Test
    void oMesmoKegNaoVaiEmDuasCargas() throws Exception {
        // Entrega prometida duas vezes, e uma delas vai faltar.
        var session = login();
        var keg = vasilhames.pronto("KEG-X", new BigDecimal("50"));
        var primeira = planeja(session);
        poeNaCarga(session, primeira, adicionaParada(session, primeira, cliente(session), 1), keg);

        var segunda = planeja(session);
        var parada = adicionaParada(session, segunda, cliente(session), 1);
        mockMvc.perform(post(BASE + "/" + segunda + "/stops/" + parada + "/containers")
                        .session(session).with(csrf()).contentType("application/json")
                        .content("{\"containerId\":\"%s\"}".formatted(keg)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reasonCode", is("already_loaded")));
    }

    @Test
    void oRoteiroSaiNaOrdemDaSequencia() throws Exception {
        var session = login();
        var carga = planeja(session);
        var cliente = cliente(session);
        adicionaParada(session, carga, cliente, 3);
        adicionaParada(session, carga, cliente, 1);

        mockMvc.perform(get(BASE + "/" + carga).session(session))
                .andExpect(jsonPath("$.route[0].sequence", is(1)))
                .andExpect(jsonPath("$.route[1].sequence", is(3)));
    }

    @Test
    void duasParadasNaMesmaPosicaoNaoConvivem() throws Exception {
        // Ambiguidade que o motorista resolve inventando — e a rota que ele inventar não é a que a
        // janela combinada pressupõe.
        var session = login();
        var carga = planeja(session);
        var cliente = cliente(session);
        adicionaParada(session, carga, cliente, 1);

        mockMvc.perform(post(BASE + "/" + carga + "/stops").session(session).with(csrf())
                        .contentType("application/json").content(corpoParada(cliente, 1)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void outraCervejariaNaoVeNemMexeNaCargaAlheia() throws Exception {
        var session = login();
        var carga = planeja(session);
        var estranho = principal(UUID.randomUUID(),
                Set.of("distribution.load.read", "distribution.load.plan"));

        mockMvc.perform(get(BASE + "/" + carga).with(authentication(estranho)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(BASE + "/" + carga + "/cancel").with(authentication(estranho))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    // --- cenário ---

    private String cargaPronta(MockHttpSession session) throws Exception {
        var carga = planeja(session);
        var parada = adicionaParada(session, carga, cliente(session), 1);
        poeNaCarga(session, carga, parada, vasilhames.pronto("KEG-" + UUID.randomUUID(),
                new BigDecimal("50")));
        atribui(session, carga);
        return carga;
    }

    private void libera(String carga) throws Exception {
        mockMvc.perform(post(BASE + "/" + carga + "/release")
                        .with(authentication(conferente(breweryOf(carga)))).with(csrf()))
                .andExpect(status().isNoContent());
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

    /**
     * Vasilhames roteirizados.
     *
     * <p>Mesmo motivo do dublê da CON-002: o que estes testes provam é <strong>como a carga reage a cada
     * impedimento</strong>, e montar um keg cheio de verdade exigiria o cenário inteiro de envase. Os
     * códigos de impedimento são os mesmos que o {@code ContainerShippingService} compõe.
     */
    static class Vasilhames implements ContainerShippingLookup {

        private final Map<UUID, ShippableContainer> kegs = new ConcurrentHashMap<>();

        UUID pronto(String code, BigDecimal volume) {
            var id = UUID.randomUUID();
            kegs.put(id, new ShippableContainer(id, code, "L-1", volume, true, Optional.empty()));
            return id;
        }

        UUID bloqueado(String code, String blocker, String mensagem) {
            var id = UUID.randomUUID();
            kegs.put(id, new ShippableContainer(id, code, "L-1", new BigDecimal("50"), false,
                    Optional.of(new Blocker(blocker, mensagem))));
            return id;
        }

        void bloqueia(UUID id, String blocker, String mensagem) {
            var atual = kegs.get(id);
            kegs.put(id, new ShippableContainer(id, atual.code(), atual.lotCode(),
                    atual.volumeLiters(), false, Optional.of(new Blocker(blocker, mensagem))));
        }

        @Override
        public Optional<ShippableContainer> shippable(UUID breweryId, UUID containerId) {
            return Optional.ofNullable(kegs.get(containerId));
        }
    }

    /**
     * Registra os movimentos pedidos, em vez de executá-los.
     *
     * <p>O efeito no ciclo do vasilhame é do {@code ContainerIT}, que o exercita de verdade. O que estes
     * testes precisam provar é que a distribuição <strong>pede</strong> o movimento certo, com os kegs
     * certos — e um dublê torna a asserção direta em vez de indireta.
     */
    static class Movimentos implements ContainerMovementCommands {

        final java.util.List<String> chamadas = java.util.Collections.synchronizedList(
                new java.util.ArrayList<>());

        @Override
        public void dispatch(UUID breweryId, java.util.List<UUID> containerIds) {
            containerIds.forEach(id -> chamadas.add("dispatch:" + id));
        }

        @Override
        public void deliver(UUID breweryId, java.util.List<UUID> containerIds) {
            containerIds.forEach(id -> chamadas.add("deliver:" + id));
        }

        @Override
        public void collect(UUID breweryId, java.util.List<UUID> containerIds) {
            containerIds.forEach(id -> chamadas.add("collect:" + id));
        }
    }

    @TestConfiguration
    static class ScriptedContainers {

        @Bean
        @Primary
        Vasilhames vasilhames() {
            return new Vasilhames();
        }

        @Bean
        @Primary
        Movimentos movimentos() {
            return new Movimentos();
        }
    }
}
