package br.com.brew.brassia.community;

import static org.assertj.core.api.Assertions.assertThat;
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
 * Links compartilhados de ponta a ponta (COM-002).
 *
 * <p>O que estes testes fixam: <strong>o link abre o que já era alcançável, e nada além</strong> — e
 * fechar a publicação derruba todos de uma vez.
 */
@SpringBootTest
@Testcontainers
class ShareLinkIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String LIBRARY = "/api/v1/community/library";
    private static final String SHARED = "/api/v1/community/shared";

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
    void oTokenApareceUmaVezEAbreAPublicacao() throws Exception {
        var session = login();
        var publicacao = publica(session, "LINK");
        var token = criaLink(session, publicacao, "READ", null);
        var deFora = principal(UUID.randomUUID(), Set.of("community.library.read"));

        mockMvc.perform(get(SHARED).param("token", token).with(authentication(deFora)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(publicacao)))
                .andExpect(jsonPath("$.mayComment", is(false)));

        // O banco guarda só o hash: o valor legível não está lá.
        var noBanco = jdbc.sql("SELECT COUNT(*) FROM community_share_link WHERE token_hash = :t")
                .param("t", token).query(Integer.class).single();
        assertThat(noBanco).isZero();
    }

    @Test
    void semTokenOLinkNaoAbreNadaPeloEndereco() throws Exception {
        // A correção da fronteira que a COM-001 deixou frouxa: LINK exige o segredo.
        var session = login();
        var publicacao = publica(session, "LINK");
        var deFora = principal(UUID.randomUUID(), Set.of("community.library.read"));

        mockMvc.perform(get(LIBRARY + "/" + publicacao).with(authentication(deFora)))
                .andExpect(status().isNotFound());
    }

    @Test
    void tokenInventadoNaoAbreEResponde404() throws Exception {
        var deFora = principal(UUID.randomUUID(), Set.of("community.library.read"));

        mockMvc.perform(get(SHARED).param("token", "nao-existe").with(authentication(deFora)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("share_link_invalid")));
    }

    @Test
    void revogarCortaNaHora() throws Exception {
        var session = login();
        var publicacao = publica(session, "LINK");
        var token = criaLink(session, publicacao, "READ", null);
        var deFora = principal(UUID.randomUUID(), Set.of("community.library.read"));
        var linkId = idDoLink(session, publicacao);

        mockMvc.perform(get(SHARED).param("token", token).with(authentication(deFora)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/community/links/" + linkId + "/revoke").session(session)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(SHARED).param("token", token).with(authentication(deFora)))
                .andExpect(status().isNotFound());
    }

    @Test
    void oLinkExpiradoNaoAbre() throws Exception {
        // O prazo combinado, e não o arrependimento — as duas coisas existem porque são diferentes.
        var session = login();
        var publicacao = publica(session, "LINK");
        var token = criaLink(session, publicacao, "READ", Instant.now().plus(Duration.ofHours(1)));
        var deFora = principal(UUID.randomUUID(), Set.of("community.library.read"));

        mockMvc.perform(get(SHARED).param("token", token).with(authentication(deFora)))
                .andExpect(status().isOk());

        // Envelhece o link inteiro: criado há duas horas, com validade de uma. Empurrar só a validade
        // para o passado seria um estado impossível — e o CHECK `expires_at > created_at` recusou a
        // primeira versão deste teste, que é exatamente o trabalho dele.
        jdbc.sql("""
                UPDATE community_share_link
                SET created_at = now() - interval '2 hours', expires_at = now() - interval '1 hour'
                WHERE publication_id = :p
                """)
                .param("p", UUID.fromString(publicacao)).update();

        mockMvc.perform(get(SHARED).param("token", token).with(authentication(deFora)))
                .andExpect(status().isNotFound());
    }

    @Test
    void fecharAPublicacaoDerrubaTodosOsLinksDeUmaVez() throws Exception {
        // Sem revogar um por um: é o botão de pânico do autor.
        var session = login();
        var publicacao = publica(session, "LINK");
        var um = criaLink(session, publicacao, "READ", null);
        var dois = criaLink(session, publicacao, "COMMENT", null);
        var deFora = principal(UUID.randomUUID(), Set.of("community.library.read"));

        mockMvc.perform(get(SHARED).param("token", um).with(authentication(deFora)))
                .andExpect(status().isOk());

        mockMvc.perform(put(LIBRARY + "/" + publicacao + "/visibility").session(session).with(csrf())
                        .contentType("application/json").content("{\"visibility\":\"PRIVATE\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(SHARED).param("token", um).with(authentication(deFora)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(SHARED).param("token", dois).with(authentication(deFora)))
                .andExpect(status().isNotFound());
    }

    @Test
    void despublicarTambemDerruba() throws Exception {
        var session = login();
        var publicacao = publica(session, "LINK");
        var token = criaLink(session, publicacao, "READ", null);
        var deFora = principal(UUID.randomUUID(), Set.of("community.library.read"));

        mockMvc.perform(post(LIBRARY + "/" + publicacao + "/unpublish").session(session).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(SHARED).param("token", token).with(authentication(deFora)))
                .andExpect(status().isNotFound());
    }

    @Test
    void oLinkDeComentarDizQuePode() throws Exception {
        var session = login();
        var publicacao = publica(session, "LINK");
        var token = criaLink(session, publicacao, "COMMENT", null);
        var deFora = principal(UUID.randomUUID(), Set.of("community.library.read"));

        mockMvc.perform(get(SHARED).param("token", token).with(authentication(deFora)))
                .andExpect(jsonPath("$.mayComment", is(true)));
    }

    @Test
    void aListaDoAutorMostraOEstadoDeCadaLink() throws Exception {
        // É o que torna a revogação uma decisão informada, em vez de um chute entre seis linhas iguais.
        var session = login();
        var publicacao = publica(session, "LINK");
        criaLink(session, publicacao, "READ", null);

        mockMvc.perform(get(LIBRARY + "/" + publicacao + "/links").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].usable", is(true)))
                .andExpect(jsonPath("$[0].label", is("pro Bruno avaliar")))
                // O token NUNCA volta numa listagem: ele apareceu uma vez, na criação.
                .andExpect(jsonPath("$[0].token").doesNotExist());
    }

    @Test
    void outraCervejariaNaoCriaNemRevogaLinkAlheio() throws Exception {
        var session = login();
        var publicacao = publica(session, "LINK");
        var linkId = criaLinkERetornaId(session, publicacao);

        mockMvc.perform(post(LIBRARY + "/" + publicacao + "/links")
                        .with(authentication(principal(UUID.randomUUID(),
                                Set.of("community.recipe.publish"))))
                        .with(csrf()).contentType("application/json")
                        .content("{\"permission\":\"READ\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/community/links/" + linkId + "/revoke")
                        .with(authentication(principal(UUID.randomUUID(),
                                Set.of("community.recipe.publish"))))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    // --- cenário ---

    private String criaLink(MockHttpSession session, String publicacao, String permissao,
            Instant expira) throws Exception {
        var corpo = expira == null
                ? "{\"permission\":\"%s\",\"label\":\"pro Bruno avaliar\"}".formatted(permissao)
                : "{\"permission\":\"%s\",\"label\":\"pro Bruno avaliar\",\"expiresAt\":\"%s\"}"
                        .formatted(permissao, expira);
        var body = mockMvc.perform(post(LIBRARY + "/" + publicacao + "/links").session(session)
                        .with(csrf()).contentType("application/json").content(corpo))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("token").asText();
    }

    private String criaLinkERetornaId(MockHttpSession session, String publicacao) throws Exception {
        var body = mockMvc.perform(post(LIBRARY + "/" + publicacao + "/links").session(session)
                        .with(csrf()).contentType("application/json")
                        .content("{\"permission\":\"READ\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("id").asText();
    }

    private String idDoLink(MockHttpSession session, String publicacao) throws Exception {
        var body = mockMvc.perform(get(LIBRARY + "/" + publicacao + "/links").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get(0).get("id").asText();
    }

    private String publica(MockHttpSession session, String visibilidade) throws Exception {
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var equipamento = idOf(mockMvc.perform(post("/api/v1/equipment").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"code":"BH-%s","name":"BH","capacityLiters":900,"deadSpaceLiters":20,
                                 "mashEfficiencyPercent":72,"boilOffLitersPerHour":8}
                                """.formatted(sfx.toUpperCase(java.util.Locale.ROOT))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        var ingrediente = idOf(mockMvc.perform(post("/api/v1/catalog/ingredients").session(session)
                        .with(csrf()).contentType("application/json")
                        .content("""
                                {"type":"MALT","code":"MALTE-%s","name":"Malte Pilsen %s",
                                 "useUnit":"KG","purchaseUnit":"KG",
                                 "attributes":{"potentialSg":"1.037","colorEbc":"3"}}
                                """.formatted(sfx.toUpperCase(java.util.Locale.ROOT), sfx)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        var receita = idOf(mockMvc.perform(post("/api/v1/recipes").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"name":"IPA %s","equipmentId":"%s","batchVolumeLiters":400,
                                 "boilTimeMinutes":60,
                                 "items":[{"ingredientId":"%s","stage":"MASH","quantity":20,"unit":"KG"}]}
                                """.formatted(sfx, equipamento, ingrediente)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/v1/recipes/" + receita + "/publish").session(session).with(csrf()))
                .andExpect(status().is2xxSuccessful());

        var body = mockMvc.perform(post(LIBRARY).session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"recipeId":"%s","title":"IPA %s","license":"CC0","visibility":"%s"}
                                """.formatted(receita, sfx, visibilidade)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("id").asText();
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
