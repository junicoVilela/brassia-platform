package br.com.brew.brassia.optimization;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import br.com.brew.brassia.shared.security.SecurityPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.UUID;
import org.assertj.core.api.Assertions;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Otimização assistida de ponta a ponta (OPT-001).
 *
 * <p>O que só aparece aqui: a <strong>reprodutibilidade contra dados reais</strong> — duas corridas sobre
 * a mesma entrada devolvem o mesmo resultado na mesma ordem, com a mesma marca de catálogo — e a fronteira
 * da IA verificada sobre o que fica gravado: anexar explicação não muda nenhum score no banco.
 */
@SpringBootTest
@Testcontainers
class OptimizationIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String RUNS = "/api/v1/optimizations";

    @Autowired WebApplicationContext context;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("otimizar por custo devolve 201 com método e versões registrados")
    void otimizaPorCusto() throws Exception {
        var session = login();
        var recipeId = publishedRecipe(session);

        optimize(session, recipeId, "COST", "[]")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.method").value("EXHAUSTIVE_SINGLE_SUBSTITUTION"))
                .andExpect(jsonPath("$.objective").value("COST"))
                .andExpect(jsonPath("$.catalogVersion").isNotEmpty())
                .andExpect(jsonPath("$.recipeVersion").value(1))
                // Método determinístico: a ausência de semente é registrada, não omitida.
                .andExpect(jsonPath("$.seed").doesNotExist())
                .andExpect(jsonPath("$.usesSeed").value(false));
    }

    @Test
    @DisplayName("A MESMA ENTRADA DEVOLVE O MESMO RESULTADO, na mesma ordem")
    void reprodutivel() throws Exception {
        // É o que torna o número auditável seis meses depois. Um solver com ordem instável falharia aqui
        // exatamente onde a diferença "não importa" — que é o pior lugar para descobrir isso.
        var session = login();
        var recipeId = publishedRecipe(session);

        var primeira = body(optimize(session, recipeId, "COST", "[]").andExpect(status().isCreated()));
        var segunda = body(optimize(session, recipeId, "COST", "[]").andExpect(status().isCreated()));

        Assertions.assertThat(segunda.get("catalogVersion").asText())
                .isEqualTo(primeira.get("catalogVersion").asText());
        Assertions.assertThat(segunda.get("candidates").toString())
                .isEqualTo(primeira.get("candidates").toString());
    }

    @Test
    @DisplayName("as alternativas trazem TRADE-OFFS junto com o ganho")
    void alternativasComTradeOffs() throws Exception {
        // Uma alternativa que aparece só com o ganho faz escolher sem saber o que se está trocando.
        var session = login();
        var recipeId = publishedRecipe(session);

        var run = body(optimize(session, recipeId, "COST", "[]").andExpect(status().isCreated()));

        if (run.get("feasible").asBoolean()) {
            for (var candidate : run.get("candidates")) {
                Assertions.assertThat(candidate.has("tradeOffs")).isTrue();
                Assertions.assertThat(candidate.get("score").isNumber()).isTrue();
                Assertions.assertThat(candidate.get("substitutions")).isNotEmpty();
            }
        }
    }

    @Test
    @DisplayName("RESTRIÇÃO IMPOSSÍVEL devolve inviabilidade explicada, não erro")
    void inviabilidadeExplicada() throws Exception {
        // "Nenhuma combinação respeita estas restrições" é resultado, não falha: 201 com o motivo.
        var session = login();
        var recipeId = publishedRecipe(session);
        var impossivel = """
                [{"kind":"MAX_COST_PER_LITER","maxValue":0.0001},
                 {"kind":"IBU_RANGE","minValue":900,"maxValue":1000}]
                """;

        optimize(session, recipeId, "COST", impossivel)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.feasible").value(false))
                .andExpect(jsonPath("$.candidates.length()").value(0))
                .andExpect(jsonPath("$.infeasible.conflictingConstraints").isNotEmpty())
                .andExpect(jsonPath("$.infeasible.explanation").isNotEmpty());
    }

    @Test
    @DisplayName("A EXPLICAÇÃO DA IA NÃO ALTERA NENHUM SCORE GRAVADO")
    void explicacaoNaoAlteraScore() throws Exception {
        // Verificado sobre o que fica no banco: relê-se a corrida depois de anexar o texto.
        var session = login();
        var recipeId = publishedRecipe(session);
        var run = body(optimize(session, recipeId, "COST", "[]").andExpect(status().isCreated()));
        var id = run.get("id").asText();
        var candidatasAntes = run.get("candidates").toString();

        mockMvc.perform(post(RUNS + "/" + id + "/explanation").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"explanation\":\"A troca reduz o custo mantendo a cor na faixa.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.explanation").isNotEmpty());

        var relido = body(mockMvc.perform(get(RUNS + "/" + id).session(session))
                .andExpect(status().isOk()));

        Assertions.assertThat(relido.get("candidates").toString()).isEqualTo(candidatasAntes);
    }

    @Test
    @DisplayName("explicação vazia é recusada no contrato")
    void explicacaoVazia() throws Exception {
        var session = login();
        var recipeId = publishedRecipe(session);
        var id = body(optimize(session, recipeId, "COST", "[]").andExpect(status().isCreated()))
                .get("id").asText();

        mockMvc.perform(post(RUNS + "/" + id + "/explanation").session(session).with(csrf())
                        .contentType("application/json").content("{\"explanation\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("aplicar registra o ponteiro para a versão de receita criada por fora")
    void aplicarRegistraPonteiro() throws Exception {
        var session = login();
        var recipeId = publishedRecipe(session);
        var run = body(optimize(session, recipeId, "COST", "[]").andExpect(status().isCreated()));
        var id = run.get("id").asText();
        // Asserção e não premissa: a receita tem dois maltes do mesmo tipo, então existe substituição.
        // Um `assumeTrue` aqui faria o teste PULAR em silêncio caso o solver regredisse — e teste pulado
        // parece cobertura.
        Assertions.assertThat(run.get("feasible").asBoolean())
                .as("a receita de teste tem dois maltes intercambiáveis; deve haver alternativa")
                .isTrue();
        var novaVersao = UUID.randomUUID();

        mockMvc.perform(post(RUNS + "/" + id + "/application").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"recipeVersionId\":\"" + novaVersao + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedRecipeVersionId").value(novaVersao.toString()));

        // Duas vezes não: a corrida registra uma aplicação, não um histórico de tentativas.
        mockMvc.perform(post(RUNS + "/" + id + "/application").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"recipeVersionId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("aplicar exige permissão crítica própria")
    void aplicarExigePermissao() throws Exception {
        var session = login();
        var recipeId = publishedRecipe(session);
        var id = body(optimize(session, recipeId, "COST", "[]").andExpect(status().isCreated()))
                .get("id").asText();
        var semAplicar = principal(breweryOf(session),
                Set.of("optimization.run.read", "optimization.run.execute"));

        mockMvc.perform(post(RUNS + "/" + id + "/application").with(csrf())
                        .with(authentication(semAplicar)).contentType("application/json")
                        .content("{\"recipeVersionId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("receita sem versão publicada é recusada")
    void receitaNaoPublicada() throws Exception {
        // Otimizar rascunho apontaria para uma composição que muda enquanto se otimiza.
        var session = login();

        optimize(session, UUID.randomUUID().toString(), "COST", "[]")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("unpublished_recipe"));
    }

    @Test
    @DisplayName("corrida de outra cervejaria não é visível")
    void isolaPorCervejaria() throws Exception {
        var session = login();
        var recipeId = publishedRecipe(session);
        var id = body(optimize(session, recipeId, "COST", "[]").andExpect(status().isCreated()))
                .get("id").asText();
        var outra = principal(UUID.randomUUID(), Set.of("optimization.run.read",
                "optimization.run.execute", "optimization.run.apply"));

        mockMvc.perform(get(RUNS + "/" + id).with(authentication(outra)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("sem permissão nenhuma, nada responde")
    void semPermissao() throws Exception {
        var nobody = principal(UUID.randomUUID(), Set.of());

        mockMvc.perform(get(RUNS).with(authentication(nobody)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a lista filtra por receita")
    void listaFiltra() throws Exception {
        var session = login();
        var recipeId = publishedRecipe(session);
        optimize(session, recipeId, "COST", "[]").andExpect(status().isCreated());

        mockMvc.perform(get(RUNS).param("recipeId", recipeId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get(RUNS).param("recipeId", UUID.randomUUID().toString()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("faixa invertida é recusada no contrato")
    void faixaInvertida() throws Exception {
        var session = login();
        var recipeId = publishedRecipe(session);

        optimize(session, recipeId, "COST",
                "[{\"kind\":\"IBU_RANGE\",\"minValue\":40,\"maxValue\":30}]")
                .andExpect(status().is4xxClientError());
    }

    // --- infraestrutura ---

    private ResultActions optimize(MockHttpSession session, String recipeId, String objective,
            String constraints) throws Exception {
        var body = """
                {"recipeId":"%s","objective":"%s","constraints":%s}
                """.formatted(recipeId, objective, constraints);
        return mockMvc.perform(post(RUNS).session(session).with(csrf())
                .contentType("application/json").content(body));
    }

    private JsonNode body(ResultActions actions) throws Exception {
        return JSON.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    private UUID breweryOf(MockHttpSession session) throws Exception {
        var body = mockMvc.perform(get("/api/v1/security/session").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JSON.readTree(body).get("activeBrewery").get("id").asText());
    }

    /** Uma receita publicada com dois maltes candidatos entre si — dá espaço para substituição. */
    private String publishedRecipe(MockHttpSession session) throws Exception {
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var equipmentId = idOf(mockMvc.perform(post("/api/v1/equipment").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"opt-" + sfx + "\",\"name\":\"BH\",\"capacityLiters\":500,"
                                + "\"deadSpaceLiters\":20,\"mashEfficiencyPercent\":72,"
                                + "\"boilOffLitersPerHour\":8}"))
                .andExpect(status().isCreated()));
        var maltId = createIngredient(session, "MALT", "m1-" + sfx, "KG",
                "{\"potentialSg\":\"1.037\",\"colorEbc\":\"4\"}");
        // Segundo malte: o candidato a substituto, de cor diferente — o que produz trade-off de cor.
        createIngredient(session, "MALT", "m2-" + sfx, "KG",
                "{\"potentialSg\":\"1.036\",\"colorEbc\":\"9\"}");
        var hopId = createIngredient(session, "HOP", "h-" + sfx, "G", "{\"alphaAcid\":\"12\"}");
        var yeastId = createIngredient(session, "YEAST", "y-" + sfx, "UNIT", "{\"attenuation\":\"78\"}");
        var content = """
                {"name":"OPT %s","equipmentId":"%s","batchVolumeLiters":400,"boilTimeMinutes":60,
                 "targetIbu":30,
                 "items":[{"ingredientId":"%s","stage":"MASH","quantity":20,"unit":"KG"},
                          {"ingredientId":"%s","stage":"BOIL","quantity":60,"unit":"G","timingMinutes":60},
                          {"ingredientId":"%s","stage":"FERMENTATION","quantity":1,"unit":"UNIT"}]}
                """.formatted(sfx, equipmentId, maltId, hopId, yeastId);
        var recipeId = idOf(mockMvc.perform(post("/api/v1/recipes").session(session).with(csrf())
                        .contentType("application/json").content(content))
                .andExpect(status().isCreated()));
        mockMvc.perform(post("/api/v1/recipes/" + recipeId + "/metrics").session(session).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/recipes/" + recipeId + "/publish").session(session).with(csrf()))
                .andExpect(status().isOk());
        return recipeId;
    }

    private String createIngredient(MockHttpSession session, String type, String code, String unit,
            String attributes) throws Exception {
        return idOf(mockMvc.perform(post("/api/v1/catalog/ingredients").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"type\":\"" + type + "\",\"code\":\"" + code + "\",\"name\":\"" + code
                                + "\",\"useUnit\":\"" + unit + "\",\"purchaseUnit\":\"" + unit
                                + "\",\"attributes\":" + attributes + "}"))
                .andExpect(status().isCreated()));
    }

    private static String idOf(ResultActions actions) throws Exception {
        return JSON.readTree(actions.andReturn().getResponse().getContentAsString()).get("id").asText();
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
