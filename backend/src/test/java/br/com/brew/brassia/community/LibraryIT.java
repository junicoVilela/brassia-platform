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
 * A biblioteca de receitas de ponta a ponta (COM-001).
 *
 * <p>O que estes testes fixam é a fronteira: <strong>o que sai, o que não sai, e quem alcança</strong>.
 * É a única história do sistema em que dado de receita atravessa para fora da cervejaria.
 */
@SpringBootTest
@Testcontainers
class LibraryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String LIBRARY = "/api/v1/community/library";

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
    void oRetratoPublicoNaoCarregaCervejariaNemIdentificadorDeIngrediente() throws Exception {
        // A prova da allowlist onde ela importa: no corpo que sai pela rede.
        var session = login();
        var id = publica(session, "PUBLIC", "CC_BY");

        var corpo = mockMvc.perform(get(LIBRARY + "/" + id).session(session))
                .andExpect(status().isOk())
                // O autor é o nome de exibição de quem publicou, congelado na publicação.
                .andExpect(jsonPath("$.author", is("Administrador Local")))
                .andExpect(jsonPath("$.licenseLabel", is("CC BY 4.0")))
                .andReturn().getResponse().getContentAsString();

        // Nem o inquilino, nem a chave do catálogo, nem a receita interna.
        assertThat(corpo).doesNotContain("breweryId").doesNotContain("brewery_id");
        assertThat(corpo).doesNotContain("ingredientId");
        assertThat(corpo).doesNotContain("recipeId");
        // E nada de custo, fornecedor ou estoque — o critério da história é literal.
        assertThat(corpo.toLowerCase()).doesNotContain("cost").doesNotContain("supplier")
                .doesNotContain("stock");
        // O que SAI: nome do ingrediente, que é o que outro cervejeiro precisa.
        assertThat(corpo).contains("ingredientName");
    }

    /**
     * Trocar a licença da própria publicação.
     *
     * <p>O endpoint não tinha teste. A troca vale <strong>daqui para a frente</strong>: quem já leu a
     * receita sob a licença anterior leu sob ela, e nada aqui desfaz isso — é o mesmo princípio do
     * despublicar, que também não apaga o que já saiu.
     *
     * <p>A parte que precisa de guarda é a de fora: relicenciar publicação alheia é 404, e não 403, pela
     * mesma razão de sempre — um 403 confirmaria que a publicação existe.
     */
    @Test
    void aLicencaSeTrocaEDaquiParaFrente() throws Exception {
        var session = login();
        var id = publica(session, "PUBLIC", "CC_BY");

        mockMvc.perform(get(LIBRARY + "/" + id).session(session))
                .andExpect(jsonPath("$.licenseLabel", is("CC BY 4.0")));

        mockMvc.perform(put(LIBRARY + "/" + id + "/license").session(session).with(csrf())
                        .contentType("application/json").content("{\"license\":\"CC_BY_SA\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(LIBRARY + "/" + id).session(session))
                .andExpect(jsonPath("$.licenseLabel", is("CC BY-SA 4.0")));
    }

    @Test
    void naoSeRelicenciaPublicacaoAlheia() throws Exception {
        var session = login();
        var id = publica(session, "PUBLIC", "CC_BY");
        var deFora = principal(UUID.randomUUID(), Set.of("community.recipe.publish"));

        mockMvc.perform(put(LIBRARY + "/" + id + "/license").with(authentication(deFora)).with(csrf())
                        .contentType("application/json").content("{\"license\":\"CC0\"}"))
                .andExpect(status().isNotFound());

        // E continua como estava: a recusa é sobre o efeito, não sobre o código de resposta.
        mockMvc.perform(get(LIBRARY + "/" + id).session(session))
                .andExpect(jsonPath("$.licenseLabel", is("CC BY 4.0")));
    }

    @Test
    void aMatrizDeVisibilidadeDecideQuemAlcanca() throws Exception {
        // O plano de testes pede a matriz inteira. Aqui ela é exercida de fora: outra cervejaria.
        var session = login();
        var deFora = principal(UUID.randomUUID(), Set.of("community.library.read"));

        // PRIVATE e BREWERY não alcançam de fora. LINK TAMBÉM NÃO, por endereço: ele exige o token
        // (COM-002) — na COM-001 ele abria para qualquer autenticado que soubesse o id, que é
        // semântica de UNLISTED e não de LINK.
        for (var nivel : new String[] {"PRIVATE", "BREWERY", "LINK"}) {
            var id = publica(session, nivel, "CC0");
            mockMvc.perform(get(LIBRARY + "/" + id).with(authentication(deFora)))
                    .andExpect(status().isNotFound());
        }
        // UNLISTED abre por endereço direto, sem segredo. PUBLIC idem, e ainda aparece na busca.
        for (var nivel : new String[] {"UNLISTED", "PUBLIC"}) {
            var id = publica(session, nivel, "CC0");
            mockMvc.perform(get(LIBRARY + "/" + id).with(authentication(deFora)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void soOPublicoApareceNaVitrine() throws Exception {
        // LINK e UNLISTED são alcançáveis por endereço e NÃO listados: quem não tem o endereço não
        // descobre que existem.
        var session = login();
        var link = publica(session, "LINK", "CC0");
        var publico = publica(session, "PUBLIC", "CC0");
        var deFora = principal(UUID.randomUUID(), Set.of("community.library.read"));

        mockMvc.perform(get(LIBRARY + "?limit=50").with(authentication(deFora)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + publico + "')]").exists())
                .andExpect(jsonPath("$[?(@.id=='" + link + "')]", is(java.util.List.of())));
    }

    @Test
    void privadaNaoExisteParaQuemEstaDeFora() throws Exception {
        // 404, e não 403: distinguir permitiria enumerar o que as outras cervejarias têm sem ler nada.
        var session = login();
        var id = publica(session, "PRIVATE", "CC0");

        mockMvc.perform(get(LIBRARY + "/" + id)
                        .with(authentication(principal(UUID.randomUUID(),
                                Set.of("community.library.read")))))
                .andExpect(status().isNotFound());
    }

    @Test
    void despublicarTiraDeCirculacaoSemApagar() throws Exception {
        var session = login();
        var id = publica(session, "PUBLIC", "CC_BY");
        var deFora = principal(UUID.randomUUID(), Set.of("community.library.read"));

        mockMvc.perform(post(LIBRARY + "/" + id + "/unpublish").session(session).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(LIBRARY + "/" + id).with(authentication(deFora)))
                .andExpect(status().isNotFound());

        // Mas continua na estante do autor, com a data de saída: o que foi publicado foi publicado.
        mockMvc.perform(get(LIBRARY + "/mine").session(session))
                .andExpect(jsonPath("$[?(@.id=='" + id + "')].published", is(java.util.List.of(false))));
    }

    @Test
    void aMesmaVersaoNaoSePublicaDuasVezes() throws Exception {
        // Duas entradas da mesma versão concorreriam na busca, possivelmente com títulos diferentes, e
        // ninguém saberia qual é a boa.
        var session = login();
        var receita = receita(session);
        publicaReceita(session, receita, "PUBLIC", "CC0").andExpect(status().isCreated());

        publicaReceita(session, receita, "PUBLIC", "CC0")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("version_already_published")));
    }

    @Test
    void todosOsDireitosReservadosNaoAutorizaFork() throws Exception {
        var session = login();
        var comLicenca = publica(session, "PUBLIC", "CC_BY");
        var semLicenca = publica(session, "PUBLIC", "ALL_RIGHTS_RESERVED");

        mockMvc.perform(get(LIBRARY + "/" + comLicenca).session(session))
                .andExpect(jsonPath("$.forkable", is(true)));
        mockMvc.perform(get(LIBRARY + "/" + semLicenca).session(session))
                .andExpect(jsonPath("$.forkable", is(false)));
    }

    @Test
    void aVisibilidadeSeFechaDepoisDePublicada() throws Exception {
        // Fechar é sempre possível — é o botão de arrependimento.
        var session = login();
        var id = publica(session, "PUBLIC", "CC0");
        var deFora = principal(UUID.randomUUID(), Set.of("community.library.read"));

        mockMvc.perform(put(LIBRARY + "/" + id + "/visibility").session(session).with(csrf())
                        .contentType("application/json").content("{\"visibility\":\"BREWERY\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(LIBRARY + "/" + id).with(authentication(deFora)))
                .andExpect(status().isNotFound());
    }

    @Test
    void publicarExigeAlcadaPropria() throws Exception {
        // Ler a biblioteca é de todos; publicar é crítico — o que sai não volta.
        var session = login();
        var receita = receita(session);

        mockMvc.perform(post(LIBRARY)
                        .with(authentication(principal(UUID.randomUUID(),
                                Set.of("community.library.read"))))
                        .with(csrf()).contentType("application/json")
                        .content(corpo(receita, "PUBLIC", "CC0")))
                .andExpect(status().isForbidden());
    }

    @Test
    void outraCervejariaNaoAdministraAPublicacaoAlheia() throws Exception {
        var session = login();
        var id = publica(session, "PUBLIC", "CC0");

        mockMvc.perform(post(LIBRARY + "/" + id + "/unpublish")
                        .with(authentication(principal(UUID.randomUUID(),
                                Set.of("community.recipe.publish"))))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    // --- cenário ---

    static String receitaId;

    private String publica(MockHttpSession session, String visibilidade, String licenca)
            throws Exception {
        var body = publicaReceita(session, receita(session), visibilidade, licenca)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("id").asText();
    }

    private org.springframework.test.web.servlet.ResultActions publicaReceita(MockHttpSession session,
            String receitaId, String visibilidade, String licenca) throws Exception {
        return mockMvc.perform(post(LIBRARY).session(session).with(csrf())
                .contentType("application/json").content(corpo(receitaId, visibilidade, licenca)));
    }

    private String corpo(String receitaId, String visibilidade, String licenca) {
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        return """
                {"recipeId":"%s","title":"IPA da Casa %s","summary":"Uma IPA de sessão",
                 "license":"%s","visibility":"%s"}
                """.formatted(receitaId, sfx, licenca, visibilidade);
    }

    /**
     * Uma receita publicada por teste.
     *
     * <p>Cada publicação precisa de uma versão inédita — o índice único é por (receita, versão) —, então
     * a receita é nova a cada chamada, e não uma por classe.
     */
    private String receita(MockHttpSession session) throws Exception {
        // Só hexadecimal em maiúsculas: o código do ingrediente aceita [A-Z0-9-], e o do equipamento
        // segue o mesmo espírito.
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var equipamento = idOf(mockMvc.perform(post("/api/v1/equipment").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"code":"BH-%s","name":"BH","capacityLiters":900,"deadSpaceLiters":20,
                                 "mashEfficiencyPercent":72,"boilOffLitersPerHour":8}
                                """.formatted(sfx)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        // O nome do ingrediente é o que sai no retrato público — por isso ele é legível aqui, e não um
        // código aleatório: o teste da allowlist procura por ele no corpo.
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
        // Só receita PUBLICADA vai para a biblioteca: um rascunho é trabalho em andamento.
        mockMvc.perform(post("/api/v1/recipes/" + receita + "/publish").session(session).with(csrf()))
                .andExpect(status().is2xxSuccessful());
        return receita;
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
