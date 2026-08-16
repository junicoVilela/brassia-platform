package br.com.brew.brassia.community;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Fork e linhagem de ponta a ponta (COM-003).
 *
 * <p>O que estes testes fixam: a cópia é <strong>independente</strong>, a atribuição é
 * <strong>congelada</strong>, e o forkador não ganha acesso a nada do autor depois do fork.
 *
 * <p>Os testes rodam na mesma cervejaria de bootstrap — o que basta, porque o que está sendo provado
 * aqui é a independência da <em>cópia</em>, e não a fronteira entre inquilinos, que o {@code LibraryIT}
 * já cobre.
 */
@SpringBootTest
@Testcontainers
class ForkIT {

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
    void oForkCriaReceitaPropriaComAtribuicaoCongelada() throws Exception {
        var session = login();
        var cena = publica(session, "PUBLIC", "CC_BY");

        var body = mockMvc.perform(post(LIBRARY + "/" + cena.publicacao + "/fork").session(session)
                        .with(csrf()).contentType("application/json")
                        .content("{\"name\":\"Minha versão\",\"equipmentId\":\"%s\"}"
                                .formatted(cena.equipamento)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recipeId", is(notNullValue())))
                .andExpect(jsonPath("$.sourceLicense", is("CC_BY")))
                // CC BY não se propaga: o forkador escolhe a licença da receita dele.
                .andExpect(jsonPath("$.requiredLicense").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        var atribuicao = JSON.readTree(body).get("attribution").asText();
        org.assertj.core.api.Assertions.assertThat(atribuicao)
                .contains("Administrador Local").contains("CC BY 4.0");

        // A receita nova é de verdade, e é dele.
        var recipeId = JSON.readTree(body).get("recipeId").asText();
        mockMvc.perform(get("/api/v1/recipes/" + recipeId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Minha versão")));
    }

    @Test
    void aAtribuicaoSobreviveAoAutorFecharAPublicacao() throws Exception {
        // O critério da história: sem acesso futuro ao conteúdo privado. A linhagem é cópia, e não
        // ponteiro — fechar a publicação não apaga o crédito nem quebra a receita.
        var session = login();
        var cena = publica(session, "PUBLIC", "CC0");
        var recipeId = forka(session, cena);

        mockMvc.perform(put(LIBRARY + "/" + cena.publicacao + "/visibility").session(session).with(csrf())
                        .contentType("application/json").content("{\"visibility\":\"PRIVATE\"}"))
                .andExpect(status().isNoContent());

        // A receita continua lá, e a linhagem também.
        mockMvc.perform(get("/api/v1/recipes/" + recipeId).session(session))
                .andExpect(status().isOk());
        var atribuicao = jdbc.sql("""
                SELECT source_author_name FROM community_recipe_fork WHERE recipe_id = :r
                """)
                .param("r", UUID.fromString(recipeId)).query(String.class).single();
        org.assertj.core.api.Assertions.assertThat(atribuicao).isEqualTo("Administrador Local");
    }

    @Test
    void licencaQueNaoAutorizaCopiaRecusaOFork() throws Exception {
        var session = login();
        var cena = publica(session, "PUBLIC", "ALL_RIGHTS_RESERVED");

        mockMvc.perform(post(LIBRARY + "/" + cena.publicacao + "/fork").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"equipmentId\":\"%s\"}".formatted(cena.equipamento)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("fork_not_allowed")));
    }

    @Test
    void naoSeForkaOQueNaoSePodeLer() throws Exception {
        // Não é sobre licença: é a matriz de visibilidade de novo.
        var session = login();
        var cena = publica(session, "PRIVATE", "CC0");

        mockMvc.perform(post(LIBRARY + "/" + cena.publicacao + "/fork").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"equipmentId\":\"%s\"}".formatted(cena.equipamento)))
                .andExpect(status().isNotFound());
    }

    @Test
    void compartilharIgualSePropaga() throws Exception {
        // CC BY-SA existe para que derivados continuem abertos, e a resposta avisa antes de o forkador
        // descobrir a obrigação na hora de publicar.
        var session = login();
        var cena = publica(session, "PUBLIC", "CC_BY_SA");

        mockMvc.perform(post(LIBRARY + "/" + cena.publicacao + "/fork").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"equipmentId\":\"%s\"}".formatted(cena.equipamento)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requiredLicense", is("CC_BY_SA")));
    }

    @Test
    void faltandoIngredienteNoCatalogoOForkERecusadoInteiro() throws Exception {
        // Uma receita a que falta ingrediente não é incompleta, é errada — e alguém a brassaria achando
        // que é a do outro. A recusa traz a lista, que é o que a torna acionável.
        var session = login();
        var cena = publica(session, "PUBLIC", "CC0");

        // Some com o ingrediente do catálogo: é o que acontece quando quem copia não tem aquele malte.
        jdbc.sql("UPDATE catalog_ingredient SET name = 'Outro Nome Qualquer' WHERE id = :i")
                .param("i", UUID.fromString(cena.ingrediente)).update();

        mockMvc.perform(post(LIBRARY + "/" + cena.publicacao + "/fork").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"equipmentId\":\"%s\"}".formatted(cena.equipamento)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("unmapped_ingredients")))
                .andExpect(jsonPath("$.missing.length()", is(1)));
    }

    @Test
    void oNomeCasaSemDiferenciarMaiusculaOuEspaco() throws Exception {
        // Exigir igualdade exata faria o forkador criar duplicatas para casar com um espaço.
        var session = login();
        var cena = publica(session, "PUBLIC", "CC0");

        jdbc.sql("UPDATE catalog_ingredient SET name = :n WHERE id = :i")
                .param("n", "  " + cena.ingredienteNome.toUpperCase(java.util.Locale.ROOT) + " ")
                .param("i", UUID.fromString(cena.ingrediente)).update();

        mockMvc.perform(post(LIBRARY + "/" + cena.publicacao + "/fork").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"equipmentId\":\"%s\"}".formatted(cena.equipamento)))
                .andExpect(status().isCreated());
    }

    @Test
    void semNomeACopiaGanhaSufixoENaoColideComOOriginal() throws Exception {
        // Nome de receita é único por cervejaria — mas o motivo de fundo é outro: duas receitas com o
        // mesmo nome no mesmo catálogo fazem o cervejeiro pegar a errada no dia da brassa.
        var session = login();
        var cena = publica(session, "PUBLIC", "CC0");
        var recipeId = forka(session, cena);

        mockMvc.perform(get("/api/v1/recipes/" + recipeId).session(session))
                .andExpect(jsonPath("$.name", org.hamcrest.Matchers.endsWith("(cópia)")));
    }

    @Test
    void oAutorVeQuantosCopiaram() throws Exception {
        var session = login();
        var cena = publica(session, "PUBLIC", "CC0");

        mockMvc.perform(get(LIBRARY + "/" + cena.publicacao + "/forks").session(session))
                .andExpect(jsonPath("$.count", is(0)));

        forka(session, cena);

        mockMvc.perform(get(LIBRARY + "/" + cena.publicacao + "/forks").session(session))
                .andExpect(jsonPath("$.count", is(1)));
    }

    // --- cenário ---

    private record Cena(String publicacao, String equipamento, String ingrediente,
            String ingredienteNome) {}

    private String forka(MockHttpSession session, Cena cena) throws Exception {
        var body = mockMvc.perform(post(LIBRARY + "/" + cena.publicacao + "/fork").session(session)
                        .with(csrf()).contentType("application/json")
                        .content("{\"equipmentId\":\"%s\"}".formatted(cena.equipamento)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("recipeId").asText();
    }

    private Cena publica(MockHttpSession session, String visibilidade, String licenca) throws Exception {
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var maiusculo = sfx.toUpperCase(java.util.Locale.ROOT);
        var equipamento = idOf(mockMvc.perform(post("/api/v1/equipment").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"code":"BH-%s","name":"BH","capacityLiters":900,"deadSpaceLiters":20,
                                 "mashEfficiencyPercent":72,"boilOffLitersPerHour":8}
                                """.formatted(maiusculo)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        var nomeIngrediente = "Malte Pilsen " + sfx;
        var ingrediente = idOf(mockMvc.perform(post("/api/v1/catalog/ingredients").session(session)
                        .with(csrf()).contentType("application/json")
                        .content("""
                                {"type":"MALT","code":"MALTE-%s","name":"%s","useUnit":"KG",
                                 "purchaseUnit":"KG","attributes":{"potentialSg":"1.037","colorEbc":"3"}}
                                """.formatted(maiusculo, nomeIngrediente)))
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
                                {"recipeId":"%s","title":"IPA %s","license":"%s","visibility":"%s"}
                                """.formatted(receita, sfx, licenca, visibilidade)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new Cena(JSON.readTree(body).get("id").asText(), equipamento, ingrediente,
                nomeIngrediente);
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
}
