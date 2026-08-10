package br.com.brew.brassia.experiment;

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
 * Lote dividido de ponta a ponta (EXP-001).
 *
 * <p>O que só aparece aqui: a checagem de que os dois lotes são da <em>mesma receita</em>, que atravessa a
 * consulta publicada da produção; a unicidade do par ativo, que é do PostgreSQL; e o fato de a conclusão
 * sair com limitações mesmo que ninguém as tenha enviado — porque não há campo para enviá-las.
 */
@SpringBootTest
@Testcontainers
class ExperimentIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String EXPERIMENTS = "/api/v1/experiments";

    @Autowired WebApplicationContext context;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("planejar com uma variável isolada devolve 201 e já traz as limitações")
    void planejarComUmaVariavel() throws Exception {
        var session = login();
        var par = parDeLotes(session);

        plan(session, par, umFatorDiferente(), "[\"DENSITY\",\"IBU\"]", true, true)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PLANNED"))
                .andExpect(jsonPath("$.isolatedVariable.name").value("Temperatura de dry hopping"))
                // Sensorial cego e duas grandezas: o melhor desenho possível. Ainda é n=1.
                .andExpect(jsonPath("$.limitations[0].code").value("SINGLE_PAIR"))
                .andExpect(jsonPath("$.limitations[0].description").isNotEmpty())
                .andExpect(jsonPath("$.limitations.length()").value(1));
    }

    @Test
    @DisplayName("DOIS FATORES DIFERENTES: 422 dizendo quais são")
    void doisFatoresRecusados() throws Exception {
        // Com dois fatores, todo resultado tem duas explicações e nenhuma pode ser descartada.
        var session = login();
        var par = parDeLotes(session);

        var factors = """
                [{"name":"Temperatura","controlValue":"20 C","variantValue":"4 C"},
                 {"name":"Levedura","controlValue":"US-05","variantValue":"S-04"}]
                """;

        plan(session, par, factors, "[\"DENSITY\"]", true, true)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("confounded_experiment"))
                .andExpect(jsonPath("$.differingFactors.length()").value(2));
    }

    @Test
    @DisplayName("controle e variante idênticos também são recusados")
    void nenhumFatorRecusado() throws Exception {
        var session = login();
        var par = parDeLotes(session);

        var factors = "[{\"name\":\"Levedura\",\"controlValue\":\"US-05\",\"variantValue\":\"US-05\"}]";

        plan(session, par, factors, "[\"DENSITY\"]", true, true)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.differingFactors.length()").value(0));
    }

    @Test
    @DisplayName("LOTES DE RECEITAS DIFERENTES: 422 — a diferença seria das receitas, não do fator")
    void receitasDiferentesRecusadas() throws Exception {
        // O resultado errado mais convincente que este módulo poderia produzir: parece um experimento
        // correto e mede outra coisa.
        var session = login();
        var par = parDeLotes(session);
        var outro = parDeLotes(session);

        var body = planBody(par.recipeId(), par.controle(), outro.variante(), umFatorDiferente(),
                "[\"DENSITY\"]", true, true);

        mockMvc.perform(post(EXPERIMENTS).session(session).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("invalid_experiment_subject"));
    }

    @Test
    @DisplayName("A CONCLUSÃO SAI COM LIMITAÇÕES SEM NINGUÉM AS ENVIAR")
    void conclusaoCarregaLimitacoes() throws Exception {
        // Não há campo de limitações no contrato de conclusão. Elas vêm do plano — o que torna concluir
        // sem registrá-las inexprimível, em vez de algo que se pede na revisão.
        var session = login();
        var par = parDeLotes(session);
        var id = idOf(plan(session, par, umFatorDiferente(), "[\"DENSITY\"]", true, false)
                .andExpect(status().isCreated()));

        mockMvc.perform(post(EXPERIMENTS + "/" + id + "/start").session(session).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(post(EXPERIMENTS + "/" + id + "/conclusion").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"supported\":true,\"observation\":\"Variante mais aromática\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUDED"))
                .andExpect(jsonPath("$.conclusion.supported").value(true))
                .andExpect(jsonPath("$.limitations.length()").value(3));

        // E continuam lá na releitura: são derivadas do plano, não gravadas.
        mockMvc.perform(get(EXPERIMENTS + "/" + id).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limitations.length()").value(3))
                .andExpect(jsonPath("$.conclusion.observation").value("Variante mais aromática"));
    }

    @Test
    @DisplayName("concluir duas vezes responde 409 com o estado atual")
    void concluirDuasVezes() throws Exception {
        var session = login();
        var par = parDeLotes(session);
        var id = idOf(plan(session, par, umFatorDiferente(), "[\"DENSITY\"]", true, true)
                .andExpect(status().isCreated()));
        mockMvc.perform(post(EXPERIMENTS + "/" + id + "/start").session(session).with(csrf()));
        mockMvc.perform(post(EXPERIMENTS + "/" + id + "/conclusion").session(session).with(csrf())
                .contentType("application/json")
                .content("{\"supported\":true,\"observation\":\"ok\"}"));

        mockMvc.perform(post(EXPERIMENTS + "/" + id + "/conclusion").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"supported\":false,\"observation\":\"outra leitura\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("illegal_experiment_transition"))
                .andExpect(jsonPath("$.currentStatus").value("CONCLUDED"));
    }

    @Test
    @DisplayName("não se conclui o que não começou")
    void concluirSemComecar() throws Exception {
        var session = login();
        var par = parDeLotes(session);
        var id = idOf(plan(session, par, umFatorDiferente(), "[\"DENSITY\"]", true, true)
                .andExpect(status().isCreated()));

        mockMvc.perform(post(EXPERIMENTS + "/" + id + "/conclusion").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"supported\":true,\"observation\":\"ok\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.currentStatus").value("PLANNED"));
    }

    @Test
    @DisplayName("O MESMO PAR NÃO ENTRA EM DOIS EXPERIMENTOS ATIVOS")
    void parAtivoUnico() throws Exception {
        // Dois experimentos sobre o mesmo par testam variáveis diferentes nos mesmos lotes — e aí nenhuma
        // das duas está isolada. Quem decide é o índice parcial do PostgreSQL.
        var session = login();
        var par = parDeLotes(session);
        plan(session, par, umFatorDiferente(), "[\"DENSITY\"]", true, true)
                .andExpect(status().isCreated());

        var outroFator = "[{\"name\":\"Água\",\"controlValue\":\"Perfil A\",\"variantValue\":\"Perfil B\"}]";

        plan(session, par, outroFator, "[\"PH\"]", true, true)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("experiment_pair_already_active"));
    }

    @Test
    @DisplayName("abandonado libera o par e continua no histórico")
    void abandonoLiberaPar() throws Exception {
        // Abandonado não se apaga: alguém já tentou isto e parou, e a próxima pessoa merece saber.
        var session = login();
        var par = parDeLotes(session);
        var id = idOf(plan(session, par, umFatorDiferente(), "[\"DENSITY\"]", true, true)
                .andExpect(status().isCreated()));

        mockMvc.perform(post(EXPERIMENTS + "/" + id + "/abandon").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ABANDONED"));

        plan(session, par, umFatorDiferente(), "[\"DENSITY\"]", true, true)
                .andExpect(status().isCreated());

        mockMvc.perform(get(EXPERIMENTS + "/" + id).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ABANDONED"))
                .andExpect(jsonPath("$.hypothesis").isNotEmpty());
    }

    @Test
    @DisplayName("experimento de outra cervejaria não é visível nem por id")
    void isolaPorCervejaria() throws Exception {
        var session = login();
        var par = parDeLotes(session);
        var id = idOf(plan(session, par, umFatorDiferente(), "[\"DENSITY\"]", true, true)
                .andExpect(status().isCreated()));

        var outra = principal(UUID.randomUUID(),
                Set.of("experiment.plan.read", "experiment.plan.write"));

        mockMvc.perform(get(EXPERIMENTS + "/" + id).with(authentication(outra)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(EXPERIMENTS + "/" + id + "/start").with(csrf()).with(authentication(outra)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("concluir exige permissão própria, separada de planejar")
    void concluirExigePermissaoPropria() throws Exception {
        // Planejar é uma intenção; concluir define o que a cervejaria passa a acreditar sobre a receita.
        var session = login();
        var par = parDeLotes(session);
        var id = idOf(plan(session, par, umFatorDiferente(), "[\"DENSITY\"]", true, true)
                .andExpect(status().isCreated()));

        var apenasPlaneja = principal(UUID.randomUUID(),
                Set.of("experiment.plan.read", "experiment.plan.write"));

        mockMvc.perform(post(EXPERIMENTS + "/" + id + "/conclusion").with(csrf())
                        .with(authentication(apenasPlaneja)).contentType("application/json")
                        .content("{\"supported\":true,\"observation\":\"ok\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("sem permissão nenhuma, nada responde")
    void semPermissao() throws Exception {
        var nobody = principal(UUID.randomUUID(), Set.of());

        mockMvc.perform(get(EXPERIMENTS).with(authentication(nobody)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("lista filtra por receita")
    void listaFiltraPorReceita() throws Exception {
        var session = login();
        var par = parDeLotes(session);
        plan(session, par, umFatorDiferente(), "[\"DENSITY\"]", true, true)
                .andExpect(status().isCreated());

        mockMvc.perform(get(EXPERIMENTS).param("recipeId", par.recipeId()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get(EXPERIMENTS).param("recipeId", UUID.randomUUID().toString())
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("hipótese vazia é recusada no contrato")
    void hipoteseVaziaRecusada() throws Exception {
        var session = login();
        var par = parDeLotes(session);

        var body = """
                {"recipeId":"%s","hypothesis":"","controlBatchId":"%s","variantBatchId":"%s",
                 "factors":%s,"plannedMeasurements":["DENSITY"],
                 "sensoryPlanned":true,"sensoryBlind":true}
                """.formatted(par.recipeId(), par.controle(), par.variante(), umFatorDiferente());

        mockMvc.perform(post(EXPERIMENTS).session(session).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
    }

    // --- infraestrutura ---

    private static String umFatorDiferente() {
        return """
                [{"name":"Temperatura de dry hopping","controlValue":"20 C","variantValue":"4 C"},
                 {"name":"Levedura","controlValue":"US-05","variantValue":"US-05"}]
                """;
    }

    private ResultActions plan(MockHttpSession session, Par par, String factors, String measurements,
            boolean sensory, boolean blind) throws Exception {
        return mockMvc.perform(post(EXPERIMENTS).session(session).with(csrf())
                .contentType("application/json")
                .content(planBody(par.recipeId(), par.controle(), par.variante(), factors, measurements,
                        sensory, blind)));
    }

    private static String planBody(String recipeId, String control, String variant, String factors,
            String measurements, boolean sensory, boolean blind) {
        return """
                {"recipeId":"%s","hypothesis":"Dry hopping a frio preserva mais aroma cítrico",
                 "controlBatchId":"%s","variantBatchId":"%s","factors":%s,
                 "plannedMeasurements":%s,"sensoryPlanned":%s,"sensoryBlind":%s}
                """.formatted(recipeId, control, variant, factors, measurements, sensory, blind);
    }

    private record Par(String recipeId, String controle, String variante) {}

    /** Dois lotes iniciados da MESMA receita — o insumo de todo experimento de lote dividido. */
    private Par parDeLotes(MockHttpSession session) throws Exception {
        var recipeId = publishedRecipe(session);
        return new Par(recipeId, startedBatch(session, recipeId), startedBatch(session, recipeId));
    }

    private String startedBatch(MockHttpSession session, String recipeId) throws Exception {
        var orderId = idOf(mockMvc.perform(post("/api/v1/brew-orders").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"recipeId\":\"" + recipeId + "\",\"volumeLiters\":400}"))
                .andExpect(status().isCreated()));
        mockMvc.perform(post("/api/v1/brew-orders/" + orderId + "/release").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"assignedUserId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/brew-orders/" + orderId + "/start").session(session).with(csrf()))
                .andExpect(status().isOk());

        var listBody = mockMvc.perform(get("/api/v1/production/batches").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (var node : JSON.readTree(listBody)) {
            if (node.get("orderId").asText().equals(orderId)) {
                return node.get("id").asText();
            }
        }
        throw new IllegalStateException("lote não encontrado para a ordem " + orderId);
    }

    private String publishedRecipe(MockHttpSession session) throws Exception {
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var equipmentId = idOf(mockMvc.perform(post("/api/v1/equipment").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"exp-" + sfx + "\",\"name\":\"BH\",\"capacityLiters\":500,"
                                + "\"deadSpaceLiters\":20,\"mashEfficiencyPercent\":72,"
                                + "\"boilOffLitersPerHour\":8}"))
                .andExpect(status().isCreated()));
        var maltId = createIngredient(session, "MALT", "m-" + sfx, "KG",
                "{\"potentialSg\":\"1.037\",\"colorEbc\":\"4\"}");
        var hopId = createIngredient(session, "HOP", "h-" + sfx, "G", "{\"alphaAcid\":\"12\"}");
        var yeastId = createIngredient(session, "YEAST", "y-" + sfx, "UNIT", "{\"attenuation\":\"78\"}");
        var content = """
                {"name":"EXP %s","equipmentId":"%s","batchVolumeLiters":400,"boilTimeMinutes":60,
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
